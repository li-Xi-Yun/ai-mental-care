package org.lixiyun.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 专用于心理诊断流程中的线程池
 *
 * @author lixiyun
 * @since 2026-08-13 21:47
 */
@Slf4j
@Configuration
public class DiagnosisThreadPoolConfig {

    /**
     * 核心线程数 = cpu 核心数 + 1
     */
    private final int core = Runtime.getRuntime().availableProcessors() + 1;

    @Bean(name = "diagnosisThreadPoolTaskExecutor")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：线程池维护的最小线程数量，即使空闲也不会被回收
        executor.setCorePoolSize(core);
        // 最大线程数：线程池允许创建的最大线程数
        executor.setMaxPoolSize(core * 2);
        // 队列容量：用于缓存等待执行任务的队列大小
        executor.setQueueCapacity(64);
        // 线程名称前缀：便于日志跟踪
        executor.setThreadNamePrefix("DiagnosisThread-");
        // 线程空闲时间：超出核心线程数的线程空闲存活时间（秒）
        executor.setKeepAliveSeconds(120);
        // 拒绝策略：当线程池和队列都已满时如何处理新任务
        // CallerRunsPolicy：由调用者线程执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化线程池
        executor.initialize();
        return executor;
    }

}