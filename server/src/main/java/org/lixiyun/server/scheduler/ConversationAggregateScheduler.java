package org.lixiyun.server.scheduler;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import jakarta.annotation.PreDestroy;
import jodd.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.conversation.ConversationMessageProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * 语音会话聚合调度器
 * <p>基于Netty时间轮实现语音会话的延迟聚合调度，同一会话重复提交时先取消旧任务再新建定时任务</p>
 * <p>
 * 整体分为两大模块：
 * <ul>
 *     <li>语音时间轮模块：管理会话ID与定时任务的映射，支持重置/取消聚合倒计时</li>
 *     <li>语音任务执行模块：定时任务到期后，从Redis ZSet中删除会话ID并执行会话消息处理</li>
 * </ul>
 * </p>
 *
 * @author lixiyun
 * @since 2026-08-16
 */
@Slf4j
@Component
public class ConversationAggregateScheduler {

    /** 时间轮：tick间隔100ms，512个槽位，自定义命名线程 */
    private final HashedWheelTimer timer = new HashedWheelTimer(
            100, TimeUnit.MILLISECONDS, 512
    );

    /** 会话ID -> 定时任务映射 */
    private final ConcurrentMap<Long, Timeout> taskMap = new ConcurrentHashMap<>();

    private final ThreadPoolExecutor businessExecutor = new ThreadPoolExecutor(
            10, 50,
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(5000),
            new ThreadFactoryBuilder().setNameFormat("audio-aggregate-proc-%d").get(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Autowired
    private ConversationMessageProcessor conversationMessageProcessor;

    /**
     * 重置会话的聚合倒计时
     * <p>原子操作：同一会话并发调用也不会出现任务泄漏</p>
     *
     * @param conversationId 会话ID
     * @param delaySeconds   延迟秒数
     */
    public void resetAggregateTimer(Long conversationId, long delaySeconds) {
        taskMap.compute(conversationId, (id, oldTimeout) -> {
            // 取消旧任务
            if (oldTimeout != null && !oldTimeout.isExpired()) {
                boolean cancelSuccess = oldTimeout.cancel();
                if (cancelSuccess) {
                    log.debug("取消旧聚合任务，会话ID：{}", conversationId);
                } else {
                    log.debug("旧聚合任务已到期执行，无法取消，会话ID：{}", conversationId);
                }
            }

            // 提交新任务
            Timeout newTimeout = timer.newTimeout(timeout -> {
                // 提交到业务线程池，不阻塞时间轮线程
                businessExecutor.submit(() -> {
                    try {
                        executeAggregateTask(conversationId, timeout);
                    } catch (Exception e) {
                        log.error("语音聚合任务执行异常，会话ID：{}", conversationId, e);
                    }
                });
            }, delaySeconds, TimeUnit.SECONDS);

            log.debug("提交新聚合任务，会话ID：{}，延迟：{}秒", conversationId, delaySeconds);
            return newTimeout;
        });
    }

    /**
     * 会话结束时清理任务
     *
     * @param conversationId 会话ID
     */
    public void cancelTask(Long conversationId) {
        Timeout task = taskMap.remove(conversationId);
        if (task != null && !task.isExpired()) {
            task.cancel();
            log.debug("主动取消聚合任务，会话ID：{}", conversationId);
        }
    }

    /**
     * 语音任务执行流程（触发条件：定时任务到达指定时间）
     * <p>
     * 执行流程：
     * <ol>
     *     <li>使用会话ID从Redis ZSet集合中删除该会话ID</li>
     *     <li>判断删除操作的返回值：返回值等于0代表该会话ID已不存在，直接结束流程</li>
     *     <li>返回值不等于0：执行会话消息处理业务逻辑</li>
     * </ol>
     * </p>
     *
     * @param conversationId 会话ID
     */
    private void executeAggregateTask(Long conversationId, Timeout currentTimeout) {
        try {
            String zSetKey = ConversationCacheConstant.CONVERSATION_MESSAGE_ZSET_KEY_PREFIX;
            boolean removed = RedisUtils.removeFromScoredSortedSet(zSetKey, String.valueOf(conversationId));

            if (!removed) {
                log.info("会话ID已不在ZSet集合中，跳过处理，会话ID：{}", conversationId);
                return;
            }

            log.info("从ZSet集合中移除会话ID成功，开始处理会话消息，会话ID：{}", conversationId);
            conversationMessageProcessor.processConversationMessage(conversationId);
        } catch (Exception e) {
            log.error("语音聚合任务执行异常，会话ID：{}", conversationId, e);
        } finally {
            taskMap.remove(conversationId, currentTimeout);
        }
    }

    /**
     * Spring容器销毁时优雅停机
     */
    @PreDestroy
    public void destroy() {
        log.info("开始关闭语音聚合调度器...");
        // 停止时间轮，不再接收新任务
        timer.stop();
        // 关闭业务线程池，等待正在执行的任务完成
        businessExecutor.shutdown();
        try {
            if (!businessExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                businessExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            businessExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        taskMap.clear();
        log.info("语音聚合调度器已关闭");
    }
}