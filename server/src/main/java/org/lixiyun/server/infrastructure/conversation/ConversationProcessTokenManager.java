package org.lixiyun.server.infrastructure.conversation;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 会话处理令牌管理器
 * <p>负责Redis分布式竞争锁/处理令牌逻辑，独立抽离</p>
 * <p>令牌竞争逻辑可以单独复用，后续其他会话任务也可能需要"会话粒度互斥锁"</p>
 *
 * @author lixiyun
 * @since 2026-08-14
 */
@Slf4j
@Component
public class ConversationProcessTokenManager {

    /**
     * 抢占处理令牌（基于Redis CAS机制）
     * <p>通过Redis Hash的CAS操作，将处理标识从"未处理"或空值原子性地更新为"处理中"，
     * 实现会话粒度的互斥锁，防止同一会话被并发处理。</p>
     *
     * @param conversationId 会话ID
     * @return {@code true} 成功获取处理令牌；{@code false} 会话正在处理中或CAS失败
     */
    public boolean acquireProcessingToken(Long conversationId) {
        String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);
        String processFlagField = ConversationCacheConstant.HASH_FIELD_PROCESS_FLAG;
        String notProcessedValue = String.valueOf(ConversationCacheConstant.PROCESS_FLAG_NOT_PROCESSED);
        String processingValue = String.valueOf(ConversationCacheConstant.PROCESS_FLAG_PROCESSING);
        log.debug("[令牌] 尝试获取处理令牌，会话ID：{}，缓存Key：{}", conversationId, cacheKey);

        try {
            Object currentFlag = RedisUtils.getCacheMapValue(cacheKey, processFlagField);
            log.debug("[令牌] 当前处理标识：{}，会话ID：{}", currentFlag, conversationId);

            Object expectValue = (currentFlag == null || notProcessedValue.equals(currentFlag.toString()))
                    ? currentFlag : null;

            if (expectValue != null || currentFlag == null) {
                boolean success = RedisUtils.compareAndSwapMapValue(
                        cacheKey,
                        processFlagField,
                        expectValue,
                        processingValue
                );

                if (success) {
                    log.debug("成功获取处理令牌并设置为处理中，会话ID：{}", conversationId);
                    return true;
                }
                log.debug("[令牌] CAS操作失败，expectValue：{}，会话ID：{}", expectValue, conversationId);
            }

            log.debug("会话正在处理中或已处理，跳过，当前标识：{}，会话ID：{}", currentFlag, conversationId);
            return false;
        } catch (Exception e) {
            log.error("获取处理令牌异常，会话ID：{}", conversationId, e);
            return false;
        }
    }

    /**
     * 清理处理标识
     * <p>将Redis中的处理标识重置为"未处理"状态，释放处理令牌，允许后续任务继续处理该会话。</p>
     *
     * @param conversationId 会话ID
     */
    public void clearProcessingFlag(Long conversationId) {
        try {
            String cacheKey = ConversationCacheConstant.buildConversationCacheKey(conversationId);
            log.debug("[令牌] 清理处理标识，会话ID：{}，缓存Key：{}", conversationId, cacheKey);
            RedisUtils.setCacheMapValue(cacheKey, ConversationCacheConstant.HASH_FIELD_PROCESS_FLAG, ConversationCacheConstant.PROCESS_FLAG_NOT_PROCESSED);
            log.debug("清理模型处理标识成功，会话ID：{}", conversationId);
        } catch (Exception e) {
            log.error("清理处理标识异常，会话ID：{}", conversationId, e);
        }
    }

    /**
     * 将会话重新加入ZSet延迟队列
     * <p>当处理令牌抢占失败时，将当前时间加上延迟秒数作为score，
     * 把会话ID重新加入有序集合，实现延迟重试调度。</p>
     *
     * @param conversationId 会话ID
     */
    public void readdToZSetQueue(Long conversationId) {
        try {
            String zSetKey = ConversationCacheConstant.CONVERSATION_MESSAGE_ZSET_KEY_PREFIX;
            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(ConversationCacheConstant.MESSAGE_ZSET_EXPIRE_SECONDS);
            log.debug("[令牌] 重新加入ZSet队列，会话ID：{}，ZSetKey：{}，过期时间：{}", conversationId, zSetKey, expireTime);

            RedisUtils.addToScoredSortedSet(zSetKey, expireTime, String.valueOf(conversationId));
            RedisUtils.expire(zSetKey, ConversationCacheConstant.MESSAGE_ZSET_EXPIRE_SECONDS, TimeUnit.SECONDS);

            log.info("重新加入ZSet队列成功，Key：{}，延迟时间：60秒，会话ID：{}", zSetKey, conversationId);
        } catch (Exception e) {
            log.error("重新加入ZSet队列失败，会话ID：{}", conversationId, e);
        }
    }
}