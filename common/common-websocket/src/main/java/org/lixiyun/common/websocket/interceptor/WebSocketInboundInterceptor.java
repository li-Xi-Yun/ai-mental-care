package org.lixiyun.common.websocket.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * 专门处理来自客户端的WebSocket消息
 * <p>
 * 注意：由于 {@code clientInboundChannel} 配置了独立的线程池（{@code webSocketInboundExecutor}），
 * {@link #preSend} 运行在 WebSocket I/O 线程（XNIO），而 {@code @MessageMapping} 注解的
 * Controller 方法运行在 {@code ws-inbound-*} 线程池线程上。
 * SecurityContextHolder 使用 ThreadLocal 策略，因此必须在 {@link #beforeHandle} 中
 * 于线程池线程上设置 SecurityContext，否则 Controller 中无法获取当前登录用户。
 * </p>
 * @author lixiyun
 * @since 2026-03-29 22:36
 */
@Slf4j
@Component
public class WebSocketInboundInterceptor implements ExecutorChannelInterceptor {

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        Principal user = accessor.getUser();

        if (StompCommand.CONNECT.equals(command)) {
            if (user == null) {
                log.error("WebSocket客户端消息前置拦截器-建立会话失败，用户未登录");
                return null;
            }
            log.info("WebSocket客户端消息前置拦截器-建立会话，用户身份：{}", user);
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            if (user == null) {
                log.error("WebSocket客户端消息前置拦截器-订阅失败，用户未登录");
                return null;
            }
            log.info("WebSocket客户端消息前置拦截器-订阅成功，用户ID：{}，订阅路径：{}，订阅ID：{}",
                    user.getName(), accessor.getDestination(), accessor.getSubscriptionId());
        }

        if (StompCommand.UNSUBSCRIBE.equals(command)) {
            if (user == null) {
                log.error("WebSocket客户端消息前置拦截器-取消订阅失败，用户未登录");
                return null;
            }
            log.info("WebSocket客户端消息前置拦截器-取消订阅成功，用户ID：{}，订阅ID：{}",
                    user.getName(), accessor.getSubscriptionId());
        }

        return message;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null) {
            Principal user = accessor.getUser();
            if (user instanceof Authentication auth) {
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        SecurityContextHolder.clearContext();
    }

}