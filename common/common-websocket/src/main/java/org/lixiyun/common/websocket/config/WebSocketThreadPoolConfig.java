package org.lixiyun.common.websocket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * WebSocket 线程池配置
 * <p>
 * 为 WebSocket 消息代理、入站消息处理、出站消息推送分别提供独立的线程池，
 * 避免与 Spring Boot 默认的 {@code @EnableScheduling} 调度线程池混用。
 * </p>
 *
 * @author lixiyun
 * @since 2026-09-24
 */
@Configuration
public class WebSocketThreadPoolConfig {

    /**
     * WebSocket 心跳与消息代理调度线程池
     * <p>
     * 负责 STOMP 心跳的定时发送，供 {@code enableSimpleBroker} 使用。
     * </p>
     */
    @Bean("webSocketTaskScheduler")
    public ThreadPoolTaskScheduler webSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    /**
     * WebSocket 入站消息处理线程池（客户端 → 服务端）
     * <p>
     * 处理客户端发送的 STOMP 消息，如 {@code SEND}、{@code SUBSCRIBE} 等命令。
     * </p>
     */
    @Bean("webSocketInboundExecutor")
    public ThreadPoolTaskExecutor webSocketInboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ws-inbound-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * WebSocket 出站消息推送线程池（服务端 → 客户端）
     * <p>
     * 处理 {@link org.springframework.messaging.simp.SimpMessagingTemplate} 的推送调用，
     * 例如 {@code convertAndSend}、{@code convertAndSendToUser}。
     * </p>
     */
    @Bean("webSocketOutboundExecutor")
    public ThreadPoolTaskExecutor webSocketOutboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ws-outbound-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}