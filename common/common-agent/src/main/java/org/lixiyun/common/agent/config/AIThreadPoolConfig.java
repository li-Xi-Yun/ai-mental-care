package org.lixiyun.common.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 模型调用专用线程池配置
 * @author lixiyun
 * @date 2026/9/23 16:23
 */
@Slf4j
@Configuration
public class AIThreadPoolConfig {

    /**
     * AI 模型调用专用线程池
     * <p>
     * AI 模型远程调用耗时较长（几秒到几十秒），必须与通用业务线程池隔离，
     * 避免阻塞 Tomcat worker 线程或其他业务线程。
     * </p>
     * <ul>
     *     <li>核心线程数较大，应对高并发 AI 调用场景</li>
     *     <li>队列容量较小，避免积压过多等待任务</li>
     *     <li>使用 CallerRunsPolicy：队列满时由调用线程执行，提供背压</li>
     * </ul>
     */
    @Bean(name = "aiModelThreadPoolTaskExecutor")
    @ConditionalOnProperty(prefix = "thread-pool.ai-model", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ThreadPoolTaskExecutor aiModelThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("AiModel-");
        executor.setKeepAliveSeconds(180);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("AI模型调用线程池已初始化: core=8, max=32, queue=64");
        return executor;
    }

}
