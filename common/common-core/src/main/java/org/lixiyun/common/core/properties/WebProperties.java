package org.lixiyun.common.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-07-15 12:51
 */
@Data
@Component
@ConfigurationProperties(prefix = "web")
public class WebProperties {

    private String allowedOrigins;

    private String url;

    private String logLevel;

    private String timeZone;

}