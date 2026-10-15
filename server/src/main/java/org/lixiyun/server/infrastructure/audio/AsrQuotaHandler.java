package org.lixiyun.server.infrastructure.audio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.quota.QuotaConfig;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.infrastructure.ai.annotation.QuotaLimit;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.scheduler.ConversationAggregateScheduler;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ASR已识别句子提交处理器
 * <p>把 {@code AudioInputAdapter} 的 ASR 回调「持久化用户消息 + 触发 AI 聚合调度」逻辑抽为独立 Bean 方法，
 * 配额的扣减/拒绝由 {@link QuotaLimit} 切面统一拦截：</p>
 * <ul>
 *   <li>被拒（超限/资源停用）时切面推送 WebSocket 拒绝事件并返回 {@code false}，
 *       调用方据此跳过落库与管道触发（该句丢弃，不产生 AI 回复）；</li>
 *   <li>异步回调线程无请求上下文，userId/conversationId 从方法参数（ARGUMENT）解析，不能依赖 ThreadLocal。</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrQuotaHandler {

    private final ConversationMemoryMapper conversationMemoryMapper;
    private final ConversationCacheManager conversationCacheManager;
    private final ConversationAggregateScheduler aggregateScheduler;

    /**
     * 提交一段已识别语音：持久化用户消息并触发 AI 聚合调度
     * <p>配额由 {@link QuotaLimit} 切面在方法执行前扣减一次 ASR（resourceType=2）；
     * 被拒时方法体不执行，返回 {@code false}，未落库、未触发管道。</p>
     *
     * @param userId         用户ID
     * @param conversationId 会话ID
     * @param text           识别文本
     * @param currentRound   当前轮次
     * @return true=提交成功；false=配额被拒（未落库、未触发管道）
     */
    @QuotaLimit(resourceType = QuotaConfig.RESOURCE_TYPE_ASR,
                userSource = QuotaLimit.QuotaUserSource.ARGUMENT,
                userIdArgIndex = 0,
                conversationIdArgIndex = 1,
                async = true)
    public boolean commitRecognizedSentence(Long userId, Long conversationId, String text, int currentRound) {
        ConversationMemory conversationMemory = ConversationMemory.builder()
                .userId(userId)
                .conversationId(conversationId)
                .content(text)
                .type(MessageType.USER.getName())
                .state(ConversationMemory.STATE_NOT_PROCESSED)
                .roundNum(currentRound)
                .build();
        conversationMemoryMapper.insert(conversationMemory);
        log.debug("[ASR配额处理器] 用户消息保存成功，会话ID：{}，轮次：{}，消息ID：{}",
                conversationId, currentRound, conversationMemory.getId());

        conversationCacheManager.saveMessageToZSet(conversationId, LocalDateTime.now());
        aggregateScheduler.addTask(conversationId, 2);
        log.info("[ASR配额处理器] 已识别句子提交成功，会话ID：{}，轮次：{}", conversationId, currentRound);
        return true;
    }
}