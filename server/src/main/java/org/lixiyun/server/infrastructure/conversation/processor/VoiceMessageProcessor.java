package org.lixiyun.server.infrastructure.conversation.processor;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.ai.model.conversation.TextMessageProcessorModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;
import org.lixiyun.server.ai.model.processor.api.StreamEventListener;
import org.lixiyun.server.ai.model.processor.factory.AgentStreamProcessorBuilder;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.infrastructure.audio.TtsConnectionManager;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.infrastructure.conversation.ConversationStreamHolder;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
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

    public static final String NODE_NAME = "voiceMessageProcessor";

    private final TextMessageProcessorModel textMessageProcessorModel;
    private final ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;
    private final ConversationCacheManager conversationCacheManager;
    private final TtsConnectionManager ttsConnectionManager;
    private final ConversationWebSocketManager conversationWebSocketManager;
    private final ConversationStreamHolder conversationStreamHolder;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

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
        log.debug("[语音处理] 会话ID：{}，用户ID：{}，当前轮次：{}，历史消息数：{}，情绪分析数：{}",
                conversationId, userId, currentRound,
                context.getConversationHistory() != null ? context.getConversationHistory().size() : 0,
                context.getEmotionAnalyses() != null ? context.getEmotionAnalyses().size() : 0);

        try {
            String prompt = buildPrompt(context);
            log.debug("[语音处理] Prompt构建完成，长度：{}，会话ID：{}", prompt.length(), conversationId);

            AgentStreamProcessor processor = AgentStreamProcessorBuilder.create()
                    .withThinkAccumulate()
                    .withLogging(conversationId)
                    .withPersistence(conversationHistoryMessagesStorage, userId, conversationId, currentRound)
                    .withListener(buildListener(userId, conversationId, currentRound))
                    .build();
            log.debug("[语音处理] AgentStreamProcessor构建完成，会话ID：{}", conversationId);

            AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfigWithLoad(NODE_NAME);
            ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
            log.debug("[语音处理] ChatModel获取完成，模型类型：{}，会话ID：{}", aiNodeConfig.getModelType(), conversationId);

            ConversationMetadata metadata = ConversationMetadata.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .currentRound(currentRound)
                    .build();
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .addMetadata(ConversationMetadata.NAME, metadata)
                    .build();
            Flux<NodeOutput> stream = textMessageProcessorModel.stream(chatModel, prompt, aiNodeConfig, runnableConfig);
            Disposable subscribe = processor.process(stream)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();

            conversationStreamHolder.addStream(conversationId, subscribe);
            log.debug("[语音处理] 流式订阅已注册到StreamHolder，会话ID：{}", conversationId);

            log.info("AI对话语音处理器-语音消息处理完成，会话ID：{}", conversationId);
        } catch (BusinessException e) {
            log.error("AI对话语音处理器-语音消息处理业务异常，会话ID：{}，错误代码：{}，错误信息：{}", conversationId, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI对话语音处理器-语音消息处理失败，会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
            throw new BusinessException(AIChatExceptionEnum.MAIN_THREAD_EXECUTION_FAILED);
        }
    }

    /**
     * 构建流式事件监听器，处理内容分块、模型完成、异常和结束事件
     *
     * @param userId         用户ID
     * @param conversationId 会话ID
     * @param currentRound   当前轮次
     * @return 流式事件监听器
     */
    private StreamEventListener buildListener(Long userId, Long conversationId, int currentRound) {
        final StringBuilder contentBuilder = new StringBuilder();
        log.debug("[语音处理] 构建流式监听器，用户ID：{}，会话ID：{}，轮次：{}", userId, conversationId, currentRound);

        return new StreamEventListener() {
            @Override
            public void onContentChunk(String text) {
                if (isInterrupted(conversationId)) {
                    log.info("AI对话语音处理器-检测到中断标志，停止WebSocket推送，会话ID：{}", conversationId);
                    ConversationMemory finalMemory = ConversationMemory.builder()
                            .userId(userId)
                            .conversationId(conversationId)
                            .content(contentBuilder.toString())
                            .type(MessageType.ASSISTANT.getName())
                            .state(ConversationMemory.STATE_PROCESSED)
                            .roundNum(currentRound)
                            .build();

                    updateCacheHistory(conversationId, finalMemory);
                    return;
                }
                log.debug("[语音处理] 收到内容分块，长度：{}，累计长度：{}，会话ID：{}", text != null ? text.length() : 0, contentBuilder.length(), conversationId);
                contentBuilder.append(text);
                ttsConnectionManager.sendTextSegment(userId, text);
                conversationWebSocketManager.sendAudioStream(userId, conversationId, text);
            }

            @Override
            public void onModelComplete(org.springframework.ai.chat.messages.AssistantMessage message) {
                log.info("AI对话语音处理器-流式完成，会话ID：{}", conversationId);
                log.debug("[语音处理] 模型输出完成，内容长度：{}，会话ID：{}", message.getText() != null ? message.getText().length() : 0, conversationId);

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

                ttsConnectionManager.finishSynthesis(userId);
                log.debug("[语音处理] TTS合成完成，用户ID：{}，会话ID：{}", userId, conversationId);
            }

            @Override
            public void onError(Throwable err) {
                log.error("AI对话语音处理器-流式处理异常，会话ID：{}，错误：{}", conversationId, err.getMessage(), err);
            }

            @Override
            public void onFinished() {
                conversationStreamHolder.removeStream(conversationId);
                clearInterruptFlag(conversationId);
                log.info("AI对话语音处理器-流程结束，会话ID：{}", conversationId);
            }
        };
    }

    /**
     * 检查会话是否存在中断标志
     *
     * @param conversationId 会话ID
     * @return 是否被中断
     */
    private boolean isInterrupted(Long conversationId) {
        try {
            int flag = (int) conversationCacheManager.getCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_INTERRUPT_FLAG);
            boolean interrupted = flag == ConversationCacheConstant.INTERRUPT_FLAG_ACTIVE;
            log.debug("[语音处理] 检查中断标志，会话ID：{}，标志值：{}，是否中断：{}", conversationId, flag, interrupted);
            return interrupted;
        } catch (Exception e) {
            log.error("AI对话语音处理器-读取中断标志失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 清除会话的中断标志，恢复为非中断状态
     *
     * @param conversationId 会话ID
     */
    private void clearInterruptFlag(Long conversationId) {
        try {
            conversationCacheManager.updateCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_INTERRUPT_FLAG,
                    ConversationCacheConstant.INTERRUPT_FLAG_INACTIVE);
            log.info("AI对话语音处理器-清除中断标识成功，会话ID：{}", conversationId);
        } catch (Exception e) {
            log.error("AI对话语音处理器-清除中断标识失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 构建LLM提示词，拼接会话历史、情绪分析、心理诊断及当前临时消息
     *
     * @param context 会话处理上下文
     * @return 拼接后的提示词字符串
     */
    private String buildPrompt(ConversationProcessContextBO context) {
        log.debug("[语音处理] 开始构建Prompt");
        StringBuilder sb = new StringBuilder();

        if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
            sb.append("\n【会话历史上下文】\n");
            context.getConversationHistory().forEach(msg ->
                    sb.append(MessageType.getDescription(msg.getType())).append("：").append(msg.getContent()).append("\n")
            );
            log.debug("[语音处理] 拼接历史消息，数量：{}", context.getConversationHistory().size());
        }

        if (context.getEmotionAnalyses() != null && !context.getEmotionAnalyses().isEmpty()) {
            sb.append("\n【历史情绪分析结果】\n");
            context.getEmotionAnalyses().forEach(analysis ->
                    sb.append("- ").append(analysis.toString()).append("\n")
            );
            log.debug("[语音处理] 拼接情绪分析，数量：{}", context.getEmotionAnalyses().size());
        }

        if (context.getEmotionDiagnosis() != null) {
            sb.append("\n【历史心理诊断结果】\n");
            sb.append(context.getEmotionDiagnosis().toString()).append("\n");
            log.debug("[语音处理] 拼接心理诊断结果");
        }

        sb.append("本次用户发送的消息为：");
        context.getTemporaryMessages().forEach(msg ->
                sb.append(msg.getContent()).append("\n")
        );
        log.debug("[语音处理] 拼接临时消息，数量：{}", context.getTemporaryMessages().size());

        return sb.toString();
    }

    /**
     * 更新Redis缓存中的会话历史上下文，将新消息追加到已有历史列表
     *
     * @param conversationId     会话ID
     * @param conversationMemory 待追加的会话记忆
     */
    private void updateCacheHistory(Long conversationId, ConversationMemory conversationMemory) {
        log.info("AI对话语音处理器-更新Redis缓存历史上下文，会话ID：{}", conversationId);

        try {
            List<ConversationMemory> existingHistory = (List<ConversationMemory>) conversationCacheManager.getCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);
            log.debug("[语音处理] 获取现有历史消息数：{}，会话ID：{}", existingHistory != null ? existingHistory.size() : 0, conversationId);

            existingHistory.add(conversationMemory);

            conversationCacheManager.updateCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES, existingHistory);

            log.info("AI对话语音处理器-缓存历史上下文更新成功，当前消息数：{}，会话ID：{}", existingHistory.size(), conversationId);
        } catch (Exception e) {
            log.error("AI对话语音处理器-更新缓存历史上下文失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
        }
    }

}