package org.lixiyun.common.websocket.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebSocket 配置项
 *
 * @author lixiyun
 */
@Data
@ConfigurationProperties("websocket")
public class WebSocketProperties {

    /**
     * 是否开启
     */
    private Boolean enabled;

    /**
     * SockJS连接端点（兼容低版本浏览器）
     */
    private String endpoint;

    /**
     * 原生WebSocket连接端点（支持二进制帧传输，用于音频等场景）
     */
    private String nativeEndpoint;

    /**
     * 消息前缀
     */
    private String messagePrefix;

    /**
     * 用户消息发送前缀
     */
    private String userSendPrefix;

    /**
     *  设置访问源地址
     */
    private String allowedOrigins;

    /**
     * 广播消息前缀
     */
    private String messageBroadcastPrefix;

    /**
     * 用户私信前缀
     */
    private String userPrivatePrefix;
}