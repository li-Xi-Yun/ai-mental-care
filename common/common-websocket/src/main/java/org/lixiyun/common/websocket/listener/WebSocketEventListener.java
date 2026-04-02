package org.lixiyun.common.websocket.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * @author lixiyun
 * @since 2026-03-29 22:52
 */
@Slf4j
@Component
public class WebSocketEventListener {

    // 监听【连接成功】
    @EventListener(SessionConnectedEvent.class)
    public void onConnect(SessionConnectedEvent event) {
        log.info("客户端上线");
    }

    // 监听【断开连接】
    @EventListener(SessionDisconnectEvent.class)
    public void onDisconnect(SessionDisconnectEvent event) {
        log.info("客户端下线");
    }
}
