package org.lixiyun.common.websocket.config;

import org.lixiyun.common.websocket.interceptor.AuthHandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.AbstractHandshakeHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 自定义握手处理器：从 handshake attributes 中获取 Principal 并设置到 WebSocket session。
 *
 * <p>背景：Spring 6.x 的 {@link AbstractHandshakeHandler#determineUser} 默认实现只返回
 * {@code request.getPrincipal()}（HTTP 请求级别的认证），不再遍历 attributes 查找 Principal。
 * 本项目在 {@link AuthHandshakeInterceptor} 握手阶段将认证信息存入 attributes，
 * 因此需要自定义 {@code determineUser()} 从 attributes 中取出。</p>
 *
 * @author lixiyun
 */
public class AttributeAwareHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    @Nullable
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        for (Object value : attributes.values()) {
            if (value instanceof Principal principal) {
                return principal;
            }
        }
        return super.determineUser(request, wsHandler, attributes);
    }
}