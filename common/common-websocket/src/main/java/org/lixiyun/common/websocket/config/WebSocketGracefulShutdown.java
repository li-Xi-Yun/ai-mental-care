package org.lixiyun.common.websocket.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * WebSocket 优雅关闭处理器。
 * <p>
 * 监听 {@link ContextClosedEvent}（在 Spring {@code SmartLifecycle.stop()} 和
 * {@code @PreDestroy} 之前触发），主动关闭所有活跃的 WebSocket 原生连接，
 * 避免 Undertow/Xnio Worker 线程在终止过程中因 WebSocket 会话关闭回调抛出
 * {@code RejectedExecutionException: Thread is terminating}。
 * </p>
 *
 * <h3>问题根因</h3>
 * <p>Undertow 优雅关闭时，XNIO Worker 线程先进入终止状态，
 * 然后 I/O channel 的强制关闭触发 WebSocket session close 回调，
 * 该回调尝试向已终止的 WorkerThread 提交任务，导致 {@code RejectedExecutionException}。</p>
 *
 * <h3>解决方案</h3>
 * <p>在 Spring 上下文关闭事件中（比 Undertow 关闭更早）主动关闭所有 WebSocket 会话，
 * 确保 XNIO Worker 线程终止时已经没有活跃的 WebSocket 连接需要回调。</p>
 *
 * @author lixiyun
 * @since 2026-09-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketGracefulShutdown {

    private final WebSocketSessionHolder sessionHolder;

    /**
     * 在 Spring Context 关闭时（早于 Undertow 停止），主动关闭所有 WebSocket 会话。
     * <p>此时 XNIO Worker 线程仍正常运行，会话可被优雅关闭。</p>
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        log.info("开始优雅关闭WebSocket连接...");
        sessionHolder.closeAll();
        log.info("WebSocket优雅关闭完成");
    }
}