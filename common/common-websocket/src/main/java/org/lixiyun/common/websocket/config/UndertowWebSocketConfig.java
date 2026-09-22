package org.lixiyun.common.websocket.config;

import io.undertow.server.DefaultByteBufferPool;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Undertow WebSocket 缓冲区池配置
 * <p>
 * 解决 Undertow 启动时警告：
 * {@code UT026010: Buffer pool was not set on WebSocketDeploymentInfo, the default pool will be used}
 * </p>
 * <p>
 * 为 WebSocket 端点注册专用的 {@link DefaultByteBufferPool}，
 * 避免使用 Undertow 默认池在高并发场景下可能产生的内存碎片问题。
 * </p>
 *
 * @author lixiyun
 */
@Configuration
public class UndertowWebSocketConfig implements WebServerFactoryCustomizer<UndertowServletWebServerFactory> {

    @Override
    public void customize(UndertowServletWebServerFactory factory) {
        factory.addDeploymentInfoCustomizers(deploymentInfo -> {
            WebSocketDeploymentInfo wsInfo = new WebSocketDeploymentInfo();
            wsInfo.setBuffers(new DefaultByteBufferPool(
                    true,
                    1024
            ));
            deploymentInfo.addServletContextAttribute(
                    WebSocketDeploymentInfo.ATTRIBUTE_NAME, wsInfo
            );
        });
    }
}