package org.lixiyun.server.ai.saver;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 检查点清理器
 * <p>使用独立事务执行检查点释放操作，避免与图执行的事务冲突导致的"连接已关闭"问题</p>
 *
 * @author lixiyun
 * @since 2026-09-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckpointCleaner {

    private final MysqlSaver mysqlSaver;

    /**
     * 释放指定线程的检查点
     * <p>使用 {@code REQUIRES_NEW} 传播级别，确保在独立的新事务中执行，
     * 不受图执行事务的回滚或提交影响</p>
     *
     * @param runnableConfig 运行配置，包含线程ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(RunnableConfig runnableConfig) {
        try {
            mysqlSaver.release(runnableConfig);
            log.debug("[检查点清理器] Release成功，线程ID: {}", runnableConfig.threadId());
        } catch (Exception e) {
            log.error("[检查点清理器] Release失败", e);
        }
    }
}