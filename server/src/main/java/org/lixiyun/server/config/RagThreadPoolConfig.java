package org.lixiyun.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * RAG文件处理线程池配置。
 *
 * @author lixiyun
 */
@Configuration
public class RagThreadPoolConfig {

    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 8;
    private static final int QUEUE_CAPACITY = 100;
    private static final int KEEP_ALIVE_SECONDS = 60;

    @Bean(name = "ragThreadPoolTaskExecutor")
    public ThreadPoolTaskExecutor ragThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix("rag-proc-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
