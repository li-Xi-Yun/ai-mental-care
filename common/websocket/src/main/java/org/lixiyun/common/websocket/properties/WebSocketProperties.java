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
     * 路径
     */
    private String path;

    /**
     *  设置访问源地址
     */
    private String allowedOrigins;

}