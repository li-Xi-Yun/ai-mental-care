package org.lixiyun.common.websocket.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话持有者，追踪所有活跃的 WebSocket 原生连接。
 * <p>
 * 会话由 {@link WebSocketConfig#configureWebSocketTransport} 中注册的
 * {@code DecoratingHandshakeHandler} 自动维护，
 * 在 {@code afterConnectionEstablished} 时注册，
 * 在 {@code afterConnectionClosed} 时移除。
 * </p>
 *
 * @author lixiyun
 * @since 2026-09-28
 */
@Slf4j
@Component
public class WebSocketSessionHolder {

    /** key=sessionId, value=WebSocketSession */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.debug("WebSocket会话已注册：{}", session.getId());
    }

    void unregister(String sessionId) {
        sessions.remove(sessionId);
        log.debug("WebSocket会话已移除：{}", sessionId);
    }

    /**
     * 关闭所有活跃的 WebSocket 会话。
     * <p>在应用关闭时调用，确保在 Undertow/Xnio Worker 线程终止前完成会话关闭，
     * 避免 {@code RejectedExecutionException: Thread is terminating} 错误。</p>
     */
    public void closeAll() {
        log.info("开始关闭所有WebSocket会话，当前连接数：{}", sessions.size());
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            try {
                WebSocketSession session = entry.getValue();
                if (session.isOpen()) {
                    session.close(CloseStatus.GOING_AWAY);
                    log.debug("WebSocket会话已关闭：{}", entry.getKey());
                }
            } catch (IOException e) {
                log.warn("关闭WebSocket会话失败：{}", entry.getKey(), e);
            }
        }
        sessions.clear();
        log.info("所有WebSocket会话关闭完成");
    }

    public int getActiveCount() {
        return sessions.size();
    }
}