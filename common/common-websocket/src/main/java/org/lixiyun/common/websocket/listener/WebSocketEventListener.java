package org.lixiyun.common.websocket.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

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
        Principal user = event.getUser();
        if(user == null){
            log.error("WebSocket监听器-连接失败，用户未登录");
            return;
        }
        log.info("客户端上线-用户ID：{}", user.getName());
    }

    // 监听【断开连接】
    @EventListener(SessionDisconnectEvent.class)
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if(user == null){
            log.error("WebSocket监听器-断开连接失败，用户未登录");
            return;
        }
        log.info("客户端下线-用户ID{}", user.getName());
    }
}
