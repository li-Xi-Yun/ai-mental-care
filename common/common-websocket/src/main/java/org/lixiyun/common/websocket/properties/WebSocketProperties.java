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
     * WebSocket连接端口
     */
    private String endpoint;

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