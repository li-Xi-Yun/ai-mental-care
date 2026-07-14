package org.lixiyun.server.infrastructure.conversation.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.springframework.stereotype.Component;

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

    private final ConversationMemoryMapper conversationMemoryMapper;

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
            log.warn("处理上下文或临时消息为空，跳过处理");
            return;
        }

        Long conversationId = context.getConversation().getId();
        log.info("开始文本消息处理，会话ID：{}，临时消息数：{}", conversationId, context.getTemporaryMessages().size());

        try {
            // todo 构建提示词
            String prompt = buildPrompt(context);

            // todo 调用LLM生成文本回答
            String llmResponse = callLLMGenerateResponse(prompt);

            // todo 通过WebSocket流式发送消息
            sendViaWebSocket(conversationId, llmResponse);

            ConversationMemory conversationMemory = ConversationMemory.builder()
                    .conversationId(conversationId)
                    .userId(context.getConversation().getUserId())
                    .content(llmResponse)
                    .type(MessageType.ASSISTANT.getName())
                    .state(ConversationMemory.STATE_PROCESSED)
                    .roundNum(context.getConversation().getCurrentRound())
                    .build();

            conversationMemoryMapper.insert(conversationMemory);

            // 更新Redis缓存中的历史上下文
            updateCacheHistory(conversationId, conversationMemory);

            log.info("文本消息处理完成，会话ID：{}", conversationId);
        } catch (Exception e) {
            log.error("文本消息处理失败，会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
            throw new RuntimeException("文本消息处理失败", e);
        }
    }

    private String callLLMGenerateResponse(String prompt) {
        log.debug("调用LLM生成回答，提示词：{}", prompt);

        return null;
    }

    private String buildPrompt(ConversationProcessContextBO context) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户消息：").append(context.getTemporaryMessages().get(0).getContent()).append("\n");

        if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
            sb.append("\n【会话历史上下文】\n");
            context.getConversationHistory().forEach(msg ->
                    sb.append(msg.getType()).append("：").append(msg.getContent()).append("\n")
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

        sb.append("\n请基于以上信息，给出专业的心理支持建议。回复要温暖、专业、具有共情能力。");
        return sb.toString();
    }

    private void sendViaWebSocket(Long conversationId, String response) {
        log.info("WebSocket流式发送开始，会话ID：{}，消息长度：{}", conversationId, response.length());

        int chunkSize = 50;
        int totalChunks = (int) Math.ceil((double) response.length() / chunkSize);

        for (int i = 0; i < response.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, response.length());
            String chunk = response.substring(i, end);
            int currentChunk = (i / chunkSize) + 1;

            log.debug("发送文本块 {}/{}，会话ID：{}，块长度：{}", currentChunk, totalChunks, conversationId, chunk.length());

            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("WebSocket发送被中断，会话ID：{}", conversationId);
                break;
            }
        }

        log.info("WebSocket流式发送完成，会话ID：{}，总块数：{}", conversationId, totalChunks);
    }

    private void updateCacheHistory(Long conversationId, ConversationMemory conversationMemory) {
        log.info("更新Redis缓存历史上下文，会话ID：{}", conversationId);

        try {
            String cacheKey = ConversationCacheConstant.CONVERSATION_CACHE_KEY_PREFIX + conversationId;

            List<ConversationMemory> existingHistory = RedisUtils.getCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);

            existingHistory.add(conversationMemory);

            RedisUtils.setCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES, existingHistory);
            RedisUtils.expire(cacheKey, ConversationCacheConstant.CONVERSATION_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);

            log.info("缓存历史上下文更新成功，当前消息数：{}，会话ID：{}", existingHistory.size(), conversationId);
        } catch (Exception e) {
            log.error("更新缓存历史上下文失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
        }
    }

}