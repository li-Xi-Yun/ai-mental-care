package org.lixiyun.common.sms.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SMS短信 配置属性
 *
 * @author lixiyun
 * @version 4.2.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "sms")
public class SmsProperties {

    public static final String COMMON = "common";

    private Boolean enabled;

    private String region;

    /**
     * 配置节点
     * 阿里云 dysmsapi.aliyuncs.com
     * 腾讯云 sms.tencentcloudapi.com
     */
    private String endpoint;

    /**
     * key
     */
    private String accessKeyId;

    /**
     * 密匙
     */
    private String accessKeySecret;

    /**
     * 短信签名
     */
    private String signName;

    /**
     * 短信模板：key=场景(register/login)，value=模板ID
     */
    private Map<String, String> templates;

    /**
     * 短信应用ID (腾讯专属)
     */
    private String sdkAppId;

}
