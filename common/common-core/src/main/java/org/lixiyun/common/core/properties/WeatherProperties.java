package org.lixiyun.common.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 天气API配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "weather")
public class WeatherProperties {

    /**
     * 天气API地址
     */
    private String apiUrl;

    /**
     * 天气API用户ID
     */
    private String id;

    /**
     * 天气API密钥
     */
    private String key;
}
