package org.lixiyun.common.websocket.config;

import lombok.RequiredArgsConstructor;
import org.lixiyun.common.websocket.interceptor.WebSocketInboundInterceptor;
import org.lixiyun.common.websocket.interceptor.WebSocketOutboundInterceptor;
import org.lixiyun.common.websocket.properties.WebSocketProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker // 开启STOMP协议的WebSocket消息代理
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // 适用于拦截器身份校验的配置
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private ThreadPoolTaskScheduler messageBrokerTaskScheduler;
    private WebSocketProperties webSocketProperties;
//    private AuthHandshakeInterceptor authHandshakeInterceptor;
    private WebSocketInboundInterceptor webSocketInboundInterceptor;
    private WebSocketOutboundInterceptor webSocketOutboundInterceptor;
    private ThreadPoolTaskExecutor webSocketInboundExecutor;
    private ThreadPoolTaskExecutor webSocketOutboundExecutor;
    @Autowired
    public void setMessageBrokerTaskScheduler(@Qualifier("webSocketTaskScheduler") ThreadPoolTaskScheduler taskScheduler,
                                              WebSocketProperties webSocketProperties,
//                                              AuthHandshakeInterceptor authHandshakeInterceptor,
                                              WebSocketOutboundInterceptor webSocketOutboundInterceptor,
                                              WebSocketInboundInterceptor webSocketInboundInterceptor,
                                              @Qualifier("webSocketInboundExecutor") ThreadPoolTaskExecutor webSocketInboundExecutor,
                                              @Qualifier("webSocketOutboundExecutor") ThreadPoolTaskExecutor webSocketOutboundExecutor) {
        this.messageBrokerTaskScheduler = taskScheduler;
        this.webSocketProperties = webSocketProperties;
//        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.webSocketInboundInterceptor = webSocketInboundInterceptor;
        this.webSocketOutboundInterceptor = webSocketOutboundInterceptor;
        this.webSocketInboundExecutor = webSocketInboundExecutor;
        this.webSocketOutboundExecutor = webSocketOutboundExecutor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setUserDestinationPrefix(webSocketProperties.getUserSendPrefix());

        registry.setApplicationDestinationPrefixes(webSocketProperties.getMessagePrefix());

        registry.enableSimpleBroker(webSocketProperties.getMessageBroadcastPrefix(),
                        webSocketProperties.getUserPrivatePrefix())
                // 心跳配置：服务端10秒/次，客户端20秒/次
                .setHeartbeatValue(new long[]{10000, 20000})
                // 绑定调度器
                .setTaskScheduler(this.messageBrokerTaskScheduler);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册第一个连接端点：客户端通过 ws://域名/ws 连接
        registry.addEndpoint(webSocketProperties.getEndpoint())
                .setAllowedOriginPatterns(webSocketProperties.getAllowedOrigins())// 允许指定跨域请求
//                .addInterceptors(authHandshakeInterceptor)
                .withSockJS(); // 支持SockJS兼容方案：浏览器不支持WebSocket时自动降级
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketInboundInterceptor)
                .taskExecutor(webSocketInboundExecutor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // 拦截从服务端推送到客户端的消息
        registration.interceptors(webSocketOutboundInterceptor)
                .taskExecutor(webSocketOutboundExecutor);
    }
}