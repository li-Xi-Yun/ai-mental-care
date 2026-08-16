package org.lixiyun.server.infrastructure.conversation.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.websocket.utils.WebSocketUtils;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.ai.model.conversation.TextMessageProcessorModel;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.factory.InjectChatModel;
import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;
import org.lixiyun.server.ai.model.processor.api.StreamEventListener;
import org.lixiyun.server.ai.model.processor.factory.AgentStreamProcessorBuilder;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.socket.constant.AudioConstant;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 语音类型消息处理器
 * <p>主线程执行流程：
 * <ol>
 *     <li>接收参数：临时消息数据、会话历史上下文、历史情绪分析结果、历史心理诊断结果</li>
 *     <li>调用LLM生成文本回答</li>
 *     <li>将LLM输出文本送入TTS模块，执行文本转语音</li>
 *     <li>实时检测缓存内是否存在中断标志：
 *         <ul>
 *             <li>若存在中断标志：执行停止发送消息逻辑，直接跳转至DB数据存储会话上下文步骤</li>
 *             <li>若不存在中断标志：通过WebSocket以流式方式推送语音消息</li>
 *         </ul>
 *     </li>
 *     <li>持久化会话上下文数据至数据库</li>
 *     <li>更新缓存中的历史上下文数据</li>
 *     <li>清除缓存里的中断标识，流程结束</li>
 * </ol>
 * 整个流程统一兜底处理：报错捕获、重试机制，保障数据一致性。
 * </p>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Slf4j
@Component(ConversationCacheConstant.CONVERSATION_TYPE_AUDIO)
@RequiredArgsConstructor
public class VoiceMessageProcessor implements MessageProcessor {

    private final TextMessageProcessorModel textMessageProcessorModel;
    private final ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;
    private final ConversationCacheManager conversationCacheManager;
    @InjectChatModel(ChatModelType.DEEP_SEEK)
    private ChatModel chatModel;

    @Override
    public void processMessage(ConversationProcessContextBO context) {
        if (context == null || context.getTemporaryMessages() == null || context.getTemporaryMessages().isEmpty()) {
            log.error("AI对话语音处理器-临时消息为空，跳过处理");
            throw new BusinessException(AIChatExceptionEnum.TEMPORARY_MESSAGES_EMPTY);
        }

        Long conversationId = context.getConversation().getId();
        int currentRound = context.getConversation().getCurrentRound();
        Long userId = context.getConversation().getUserId();
        log.info("AI对话语音处理器-开始语音消息处理，会话ID：{}，临时消息数：{}", conversationId, context.getTemporaryMessages().size());

        try {
            String prompt = buildPrompt(context);

            AgentStreamProcessor processor = AgentStreamProcessorBuilder.create()
                    .withThinkAccumulate()
                    .withLogging(conversationId)
                    .withPersistence(conversationHistoryMessagesStorage, userId, conversationId, currentRound)
                    .withListener(buildListener(userId, conversationId, currentRound))
                    .build();

            processor.process(textMessageProcessorModel.stream(chatModel, prompt))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();

            log.info("AI对话语音处理器-语音消息处理完成，会话ID：{}", conversationId);
        } catch (BusinessException e) {
            log.error("AI对话语音处理器-语音消息处理业务异常，会话ID：{}，错误代码：{}，错误信息：{}", conversationId, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI对话语音处理器-语音消息处理失败，会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
            throw new BusinessException(AIChatExceptionEnum.MAIN_THREAD_EXECUTION_FAILED);
        }
    }

    private StreamEventListener buildListener(Long userId, Long conversationId, int currentRound) {
        return new StreamEventListener() {
            private final StringBuilder fullText = new StringBuilder();

            @Override
            public void onContentChunk(String text) {
                if (isInterrupted(conversationId)) {
                    log.info("AI对话语音处理器-检测到中断标志，停止WebSocket推送，会话ID：{}", conversationId);
                    return;
                }
                fullText.append(text);
                textToSpeech(text);
                sendViaWebSocket(userId, AudioConstant.AI_AUDIO_REPLY, conversationId, text);
            }

            @Override
            public void onModelComplete(org.springframework.ai.chat.messages.AssistantMessage message) {
                log.info("AI对话语音处理器-流式完成，会话ID：{}", conversationId);

                String textContent = message.getText();

                ConversationMemory finalMemory = ConversationMemory.builder()
                        .userId(userId)
                        .conversationId(conversationId)
                        .content(textContent)
                        .type(MessageType.ASSISTANT.getName())
                        .state(ConversationMemory.STATE_PROCESSED)
                        .roundNum(currentRound)
                        .build();

                updateCacheHistory(conversationId, finalMemory);
                clearInterruptFlag(conversationId);
            }

            @Override
            public void onError(Throwable err) {
                log.error("AI对话语音处理器-流式处理异常，会话ID：{}，错误：{}", conversationId, err.getMessage(), err);
                clearInterruptFlag(conversationId);
            }

            @Override
            public void onFinished() {
                log.info("AI对话语音处理器-流程结束，会话ID：{}", conversationId);
            }
        };
    }

    private boolean isInterrupted(Long conversationId) {
        try {
            String flag = (String) conversationCacheManager.getCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_INTERRUPT_FLAG);
            return ConversationCacheConstant.INTERRUPT_FLAG_ACTIVE.equals(flag);
        } catch (Exception e) {
            log.error("AI对话语音处理器-读取中断标志失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
            return false;
        }
    }

    private void clearInterruptFlag(Long conversationId) {
        try {
            conversationCacheManager.updateCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_INTERRUPT_FLAG,
                    ConversationCacheConstant.INTERRUPT_FLAG_INACTIVE);
            log.info("AI对话语音处理器-清除中断标识成功，会话ID：{}", conversationId);
        } catch (Exception e) {
            log.error("AI对话语音处理器-清除中断标识失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
        }
    }

    private void textToSpeech(String text) {
        // todo
    }

    private String buildPrompt(ConversationProcessContextBO context) {
        StringBuilder sb = new StringBuilder();

        if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
            sb.append("\n【会话历史上下文】\n");
            context.getConversationHistory().forEach(msg ->
                    sb.append(MessageType.getDescription(msg.getType())).append("：").append(msg.getContent()).append("\n")
            );
        }

        if (context.getEmotionAnalyses() != null && !context.getEmotionAnalyses().isEmpty()) {
            sb.append("\n【历史情绪分析结果】\n");
            context.getEmotionAnalyses().forEach(analysis ->
                    sb.append("- ").append(analysis.toString()).append("\n")
            );
        }

        if (context.getEmotionDiagnosis() != null) {
            sb.append("\n【历史心理诊断结果】\n");
            sb.append(context.getEmotionDiagnosis().toString()).append("\n");
        }

        sb.append("本次用户发送的消息为：");
        context.getTemporaryMessages().forEach(msg ->
                sb.append(msg.getContent()).append("\n")
        );

        return sb.toString();
    }

    private void sendViaWebSocket(Long userId, String webSocketId, Long conversationId, String response) {
        log.info("AI对话语音处理器-WebSocket流式发送开始，会话ID：{}，消息长度：{}", conversationId, response.length());
        WebSocketUtils.sendToUserBySubDestination(userId.toString(), webSocketId + "/" + conversationId, response);
    }

    private void updateCacheHistory(Long conversationId, ConversationMemory conversationMemory) {
        log.info("AI对话语音处理器-更新Redis缓存历史上下文，会话ID：{}", conversationId);

        try {
            List<ConversationMemory> existingHistory = (List<ConversationMemory>) conversationCacheManager.getCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);

            existingHistory.add(conversationMemory);

            conversationCacheManager.updateCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES, existingHistory);

            log.info("AI对话语音处理器-缓存历史上下文更新成功，当前消息数：{}，会话ID：{}", existingHistory.size(), conversationId);
        } catch (Exception e) {
            log.error("AI对话语音处理器-更新缓存历史上下文失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
        }
    }

}