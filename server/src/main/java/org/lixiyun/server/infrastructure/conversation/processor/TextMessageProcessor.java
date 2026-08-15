package org.lixiyun.server.infrastructure.conversation.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.redis.utils.RedisUtils;
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
import org.lixiyun.server.socket.constant.TextConstant;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 文本类型消息处理器
 * <p>主线程执行流程：
 * <ol>
 *     <li>接收参数：临时消息数据、会话历史上下文、历史情绪分析结果、历史心理诊断结果</li>
 *     <li>调用LLM生成文本回答</li>
 *     <li>通过WebSocket流式发送消息</li>
 *     <li>将回答保存到数据库</li>
 *     <li>更新Redis缓存中的历史上下文</li>
 *     <li>异常处理与数据一致性保障</li>
 * </ol>
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-14 21:36
 */
@Slf4j
@Component(ConversationCacheConstant.CONVERSATION_TYPE_TEXT)
@RequiredArgsConstructor
public class TextMessageProcessor implements MessageProcessor {

    private final TextMessageProcessorModel textMessageProcessorModel;
    private final ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;
    @InjectChatModel(ChatModelType.DEEP_SEEK)
    private ChatModel chatModel;

    /**
     * 处理文本类型的会话消息
     * <p>主线程执行流程（按图片要求）：</p>
     * <ol>
     *     <li>接收参数：临时消息数据、会话历史上下文、历史情绪分析结果、历史心理诊断结果</li>
     *     <li>调用LLM生成文本回答</li>
     *     <li>通过WebSocket流式发送消息</li>
     *     <li>将回答保存到数据库</li>
     *     <li>更新Redis缓存中的历史上下文</li>
     * </ol>
     *
     * @param context 会话消息处理上下文 {@link ConversationProcessContextBO}
     */
    @Override
    public void processMessage(ConversationProcessContextBO context) {
        if (context == null || context.getTemporaryMessages() == null || context.getTemporaryMessages().isEmpty()) {
            log.error("AI对话文本处理器-临时消息为空，跳过处理");
            throw new BusinessException(AIChatExceptionEnum.TEMPORARY_MESSAGES_EMPTY);
        }

        Long conversationId = context.getConversation().getId();
        int currentRound = context.getConversation().getCurrentRound() + 1;
        Long userId = context.getConversation().getUserId();
        log.info("AI对话文本处理器-开始文本消息处理，会话ID：{}，临时消息数：{}", conversationId, context.getTemporaryMessages().size());

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

            log.info("AI对话文本处理器-文本消息处理完成，会话ID：{}", conversationId);
        } catch (BusinessException e) {
            log.error("AI对话文本处理器-文本消息处理业务异常，会话ID：{}，错误代码：{}，错误信息：{}", conversationId, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI对话文本处理器-文本消息处理失败，会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
            throw new BusinessException(AIChatExceptionEnum.MAIN_THREAD_EXECUTION_FAILED);
        }
    }

    private StreamEventListener buildListener(Long userId, Long conversationId, int currentRound) {
        return new StreamEventListener() {
            @Override
            public void onContentChunk(String text) {
                sendViaWebSocket(userId, TextConstant.AI_TEXT_REPLY, conversationId, text);
            }

            @Override
            public void onModelComplete(org.springframework.ai.chat.messages.AssistantMessage message) {
                log.info("AI对话文本处理器-流式完成，会话ID：{}", conversationId);

                ConversationMemory finalMemory = ConversationMemory.builder()
                        .userId(userId)
                        .conversationId(conversationId)
                        .content(message.getText())
                        .type(MessageType.ASSISTANT.getName())
                        .state(ConversationMemory.STATE_PROCESSED)
                        .roundNum(currentRound)
                        .build();

                updateCacheHistory(conversationId, finalMemory);
            }
        };
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
        log.info("AI对话文本处理器-WebSocket流式发送开始，会话ID：{}，消息长度：{}", conversationId, response.length());
        WebSocketUtils.sendToUserBySubDestination(userId.toString(), webSocketId + "/" + conversationId, response);
    }

    private void updateCacheHistory(Long conversationId, ConversationMemory conversationMemory) {
        log.info("AI对话文本处理器-更新Redis缓存历史上下文，会话ID：{}", conversationId);

        try {
            String cacheKey = ConversationCacheConstant.CONVERSATION_CACHE_KEY_PREFIX + conversationId;

            List<ConversationMemory> existingHistory = RedisUtils.getCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);

            existingHistory.add(conversationMemory);

            RedisUtils.setCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES, existingHistory);
            RedisUtils.expire(cacheKey, ConversationCacheConstant.CONVERSATION_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

            log.info("AI对话文本处理器-缓存历史上下文更新成功，当前消息数：{}，会话ID：{}", existingHistory.size(), conversationId);
        } catch (Exception e) {
            log.error("AI对话文本处理器-更新缓存历史上下文失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
        }
    }

}