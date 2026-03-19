package org.lixiyun.common.core.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.lixiyun.common.core.properties.ThreadPoolProperties;
import org.lixiyun.common.core.utils.Threads;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 *
 * @author lixiyun
 **/
@Slf4j
@Configuration
@EnableConfigurationProperties(ThreadPoolProperties.class)
public class ThreadPoolConfig {

    /**
     * 核心线程数 = cpu 核心数 + 1
     */
    private final int core = Runtime.getRuntime().availableProcessors() + 1;

    @Autowired
    private ThreadPoolProperties threadPoolProperties;

    @Bean(name = "threadPoolTaskExecutor")
    @ConditionalOnProperty(prefix = "thread-pool", name = "enabled", havingValue = "true")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：线程池维护的最小线程数量，即使空闲也不会被回收
        executor.setCorePoolSize(core);
        // 最大线程数：线程池允许创建的最大线程数
        executor.setMaxPoolSize(core * 2);
        // 队列容量：用于缓存等待执行任务的队列大小
        executor.setQueueCapacity(threadPoolProperties.getQueueCapacity());
        // 线程名称前缀：便于日志跟踪
        executor.setThreadNamePrefix("WatchHistoryThread-");
        // 线程空闲时间：超出核心线程数的线程空闲存活时间（秒）
        executor.setKeepAliveSeconds(threadPoolProperties.getKeepAliveSeconds());
        // 拒绝策略：当线程池和队列都已满时如何处理新任务
        // CallerRunsPolicy：由调用者线程执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化线程池
        executor.initialize();
        return executor;
    }

    /**
     * 执行周期性或定时任务
     */
    @Bean(name = "scheduledExecutorService")
    public ScheduledExecutorService scheduledExecutorService() {
        log.info("====创建定时任务线程池====");
        return new ScheduledThreadPoolExecutor(core,
            new BasicThreadFactory.Builder().namingPattern("schedule-pool-%d").daemon(true).build(),
            new ThreadPoolExecutor.CallerRunsPolicy()) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                Threads.printException(r, t);
            }
        };
    }

    // 默认的线程池有以下缺点：
    // 每次执行都创建新线程：它不会重用线程，而是为每个任务创建新线程
    // 无限制创建线程：没有线程池机制，可能导致系统资源耗尽


    // 这是一个Spring MVC配置类，用于配置异步请求处理。
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                // 使用已定义的线程池作为异步请求处理的执行器
                configurer.setTaskExecutor(threadPoolTaskExecutor());
                // 设置异步请求的超时时间(毫秒)
                configurer.setDefaultTimeout(30000);
            }
        };
    }
    // 这是为了解决以下警告信息的
    //  - !!!
    //Performing asynchronous handling through the default Spring MVC SimpleAsyncTaskExecutor.
    //This executor is not suitable for production use under load.
    //Please, configure an AsyncTaskExecutor through the WebMvc config.
    //-------------------------------
    //!!!


}
