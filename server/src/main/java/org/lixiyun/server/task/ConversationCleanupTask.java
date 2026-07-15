package org.lixiyun.server.task;

import jodd.util.concurrent.ThreadFactoryBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.utils.DateUtils;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.conversation.ConversationMessageProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 会话消息处理定时任务
 * <p>每秒扫描缓存消息ZSet集合，获取满足时间间隔条件的会话ID并进行异步并行处理</p>
 *
 * @author lixiyun
 * @since 2026-07-14 17:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationCleanupTask {

    private final DateUtils dateUtils;
    private final ConversationMessageProcessor conversationMessageProcessor;

    private static final int SCAN_BATCH_SIZE = 50;

    private static final String ZSET_KEY = ConversationCacheConstant.CONVERSATION_MESSAGE_ZSET_KEY_PREFIX;

    private final String LUA_SCRIPT =
            "-- 1. 从ZSet中获取分数 ≤ 阈值的成员（带数量限制）\n" +
                    "local expiredIds = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, tonumber(ARGV[2]))\n" +
                    "\n" +
                    "-- 2. 有命中则删除（原子操作）\n" +
                    "if #expiredIds > 0 then\n" +
                    "    redis.call('ZREM', KEYS[1], unpack(expiredIds))\n" +
                    "end\n" +
                    "\n" +
                    "-- 3. 返回被删除的会话ID\n" +
                    "return expiredIds";

    private final ThreadPoolExecutor conversationTaskThreadPoolExecutor = new ThreadPoolExecutor(
            10, 50,                  // 核心/最大线程数，按IO密集型估算
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(5000),  // 队列有界，超了就拒绝，保护系统
            new ThreadFactoryBuilder().setNameFormat("conversation-task-proc-%d").get(),
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：调用者自己跑，起到背压作用
    );

    @Scheduled(fixedRateString = "${conversation.task.scan-interval:1000}")
    public void scanAndProcessConversationMessages() {
        log.debug("开始执行定时任务：扫描缓存消息ZSet集合");

        try {
            Set<Long> conversationIds = scanExpiredConversationsFromZSet();

            if (conversationIds.isEmpty()) {
                log.debug("没有发现满足时间条件的会话ID");
                return;
            }

            log.info("发现{}个满足时间条件的会话ID，开始异步并行处理", conversationIds.size());

            asyncParallelProcessConversations(conversationIds);

            log.info("定时任务执行完成，已触发{}个会话的消息处理", conversationIds.size());
        } catch (Exception e) {
            log.error("定时任务执行异常", e);
        }
    }

    private Set<Long> scanExpiredConversationsFromZSet() {
        log.debug("开始通过Lua脚本扫描ZSet集合，数量限制：{}", ConversationCleanupTask.SCAN_BATCH_SIZE);

        // 1. 获取当前东八区LocalDateTime
        LocalDateTime nowLocal = LocalDateTime.now();
        // 2. 转成UTC毫秒戳（和写入ZSet的转换逻辑完全统一）
        long currentUtcMilli = dateUtils.toUtcZoned(nowLocal).toInstant().toEpochMilli();
        // 3. 基于UTC时间计算过期阈值（阈值也是UTC毫秒）
        long expireThreshold = currentUtcMilli - (ConversationCacheConstant.MESSAGE_ZSET_EXPIRE_SECONDS * 1000);

        List<Object> result = RedisUtils.executeLuaScript(
                LUA_SCRIPT,
                org.redisson.api.RScript.ReturnType.MULTI,
                Collections.singletonList(ZSET_KEY),  // 传单个 key
                String.valueOf(expireThreshold),      // ARGV[1]：过期时间阈值
                String.valueOf(SCAN_BATCH_SIZE)       // ARGV[2]：每批数量
        );

        Set<Long> scannedConversationIds = result.stream()
                .filter(Objects::nonNull)
                .map(obj -> {
                    try {
                        return Long.valueOf(obj.toString().trim());
                    } catch (NumberFormatException e) {
                        log.warn("转换会话ID失败，原始值：{}", obj);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.debug("Lua脚本扫描完成，获取到{}个满足条件的会话ID", scannedConversationIds.size());
        return scannedConversationIds;
    }

    private void asyncParallelProcessConversations(Set<Long> conversationIds) {
        log.debug("开始异步并行处理{}个会话", conversationIds.size());

        conversationIds.forEach(conversationId ->
                CompletableFuture.runAsync(() -> {
                    try {
                        processConversationMessage(conversationId);
                    } catch (Exception e) {
                        log.error("异步处理会话消息异常，会话ID：{}", conversationId, e);
                    }
                }, conversationTaskThreadPoolExecutor)  // 指定专用线程池
        );
    }

    public void processConversationMessage(Long conversationId) {
        log.info("开始处理会话消息（暂未实现），会话ID：{}", conversationId);
        conversationMessageProcessor.processConversationMessage(conversationId);
    }

}