package org.lixiyun.common.websocket.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes) {
        log.info("WebSocket 连接前置拦截执行");
        // 从 SecurityContext 获取你在 Filter 中设置的用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        log.debug("WebSocket 连接前置拦截器，用户身份：{}", auth);
        if (auth != null && auth.isAuthenticated()) {
            // 【关键】使用 Spring Security 要求的 key 来存储认证信息
            // 这样 StompHeaderAccessor.getUser() 才能自动获取到用户
//            attributes.put("simpUser", auth);

            // 同时保留原始认证对象，方便后续拦截器访问
            attributes.put("SPRING_SECURITY_CONTEXT", auth);
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception ex) {}
}