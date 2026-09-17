package org.lixiyun.server.infrastructure.conversation.processor;

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
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.util.List;

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

    public static final String NODE_NAME = "textMessageProcessor";

    private final TextMessageProcessorModel textMessageProcessorModel;
    private final ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;
    private final ConversationWebSocketManager conversationWebSocketManager;
    private final ConversationCacheManager conversationCacheManager;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

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
            log.error("[AI对话文本处理器]-临时消息为空，跳过处理");
            throw new BusinessException(AIChatExceptionEnum.TEMPORARY_MESSAGES_EMPTY);
        }

        Long conversationId = context.getConversation().getId();
        int currentRound = context.getConversation().getCurrentRound();
        Long userId = context.getConversation().getUserId();
        log.info("[AI对话文本处理器]-开始文本消息处理，会话ID：{}，临时消息数：{}", conversationId, context.getTemporaryMessages().size());
        log.debug("[AI对话文本处理器] 会话ID：{}，用户ID：{}，当前轮次：{}，历史消息数：{}，情绪分析数：{}",
                conversationId, userId, currentRound,
                context.getConversationHistory() != null ? context.getConversationHistory().size() : 0,
                context.getEmotionAnalyses() != null ? context.getEmotionAnalyses().size() : 0);

        try {
            String prompt = buildPrompt(context);
            log.debug("[AI对话文本处理器] Prompt构建完成，长度：{}，会话ID：{}", prompt.length(), conversationId);

            AgentStreamProcessor processor = AgentStreamProcessorBuilder.create()
                    .withThinkAccumulate()
                    .withLogging(conversationId)
                    .withPersistence(conversationHistoryMessagesStorage, userId, conversationId, currentRound)
                    .withListener(buildListener(userId, conversationId, currentRound))
                    .build();
            log.debug("[AI对话文本处理器] AgentStreamProcessor构建完成，会话ID：{}", conversationId);

            AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
            ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
            log.debug("[AI对话文本处理器] ChatModel获取完成，模型类型：{}，会话ID：{}", aiNodeConfig.getModelType(), conversationId);

            ConversationMetadata metadata = ConversationMetadata.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .currentRound(currentRound)
                    .build();
            log.debug("[AI对话文本处理器] 构建模型调用配置完成，元数据：{}", metadata);
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .addMetadata(ConversationMetadata.NAME, metadata)
                    .build();
            processor.process(textMessageProcessorModel.stream(chatModel, prompt, aiNodeConfig, runnableConfig))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            event -> {},
                            error -> log.error("[AI对话文本处理器]-流式订阅异常（流外异常，未进入装饰器管道），会话ID：{}，用户ID：{}，错误：{}", conversationId, userId, error.getMessage(), error)
                    );

            log.info("[AI对话文本处理器]-流式订阅已启动，会话ID：{}，用户ID：{}", conversationId, userId);
        } catch (BusinessException e) {
            log.error("[AI对话文本处理器]-文本消息处理业务异常，会话ID：{}，错误代码：{}，错误信息：{}", conversationId, e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[AI对话文本处理器]-文本消息处理失败，会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
            throw new BusinessException(AIChatExceptionEnum.MAIN_THREAD_EXECUTION_FAILED);
        }
    }

    private StreamEventListener buildListener(Long userId, Long conversationId, int currentRound) {
        log.debug("[AI对话文本处理器] 构建流式监听器，用户ID：{}，会话ID：{}，轮次：{}", userId, conversationId, currentRound);
        return new StreamEventListener() {
            @Override
            public void onContentChunk(String text) {
                log.debug("[AI对话文本处理器] 收到内容分块，长度：{}，会话ID：{}", text != null ? text.length() : 0, conversationId);
                conversationWebSocketManager.sendTextStream(userId, conversationId, text);
            }

            @Override
            public void onModelComplete(org.springframework.ai.chat.messages.AssistantMessage message) {
                log.info("[AI对话文本处理器] -流式完成，会话ID：{}", conversationId);
                log.debug("[AI对话文本处理器] 模型输出完成，内容长度：{}，会话ID：{}", message.getText() != null ? message.getText().length() : 0, conversationId);

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

            @Override
            public void onError(Throwable err) {
                log.error("[AI对话文本处理器]-流式处理异常（流内异常，已进入装饰器管道），会话ID：{}，用户ID：{}，错误：{}", conversationId, userId, err.getMessage(), err);
            }

            @Override
            public void onFinished() {
                log.info("[AI对话文本处理器]-流式处理完成，会话ID：{}，用户ID：{}", conversationId, userId);
            }
        };
    }

    private String buildPrompt(ConversationProcessContextBO context) {
        log.debug("[AI对话文本处理器] 开始构建Prompt");
        StringBuilder sb = new StringBuilder();

        if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
            sb.append("\n【会话历史上下文】\n");
            context.getConversationHistory().forEach(msg ->
                    sb.append(MessageType.getDescription(msg.getType())).append("：").append(msg.getContent()).append("\n")
            );
            log.debug("[AI对话文本处理器] 拼接历史消息，数量：{}", context.getConversationHistory().size());
        }

        if (context.getEmotionAnalyses() != null && !context.getEmotionAnalyses().isEmpty()) {
            sb.append("\n【历史情绪分析结果】\n");
            context.getEmotionAnalyses().forEach(analysis ->
                    sb.append("- ").append(analysis.toString()).append("\n")
            );
            log.debug("[AI对话文本处理器] 拼接情绪分析，数量：{}", context.getEmotionAnalyses().size());
        }

        if (context.getEmotionDiagnosis() != null) {
            sb.append("\n【历史心理诊断结果】\n");
            sb.append(context.getEmotionDiagnosis().toString()).append("\n");
            log.debug("[AI对话文本处理器] 拼接心理诊断结果");
        }

        sb.append("本次用户发送的消息为：");
        context.getTemporaryMessages().forEach(msg ->
                sb.append(msg.getContent()).append("\n")
        );
        log.debug("[AI对话文本处理器] 拼接临时消息，数量：{}", context.getTemporaryMessages().size());

        return sb.toString();
    }

    private void updateCacheHistory(Long conversationId, ConversationMemory conversationMemory) {
        log.info("[AI对话文本处理器]-更新Redis缓存历史上下文，会话ID：{}", conversationId);

        try {
            List<ConversationMemory> existingHistory = (List<ConversationMemory>) conversationCacheManager.getCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);
            log.debug("[AI对话文本处理器] 获取现有历史消息数：{}，会话ID：{}", existingHistory != null ? existingHistory.size() : 0, conversationId);

            existingHistory.add(conversationMemory);

            conversationCacheManager.updateCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES, existingHistory);
            log.info("[AI对话文本处理器]-缓存历史上下文更新成功，当前消息数：{}，会话ID：{}", existingHistory.size(), conversationId);
        } catch (Exception e) {
            log.error("[AI对话文本处理器]-更新缓存历史上下文失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
        }
    }

}