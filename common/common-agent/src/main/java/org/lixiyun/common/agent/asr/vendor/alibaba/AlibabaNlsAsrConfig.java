package org.lixiyun.common.agent.asr.vendor.alibaba;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云NLS ASR配置
 * <p>绑定配置文件前缀{@code asr.alibaba}，通过{@code @ConfigurationProperties}自动注入。</p>
 *
 * @author lixiyun
 * @since 2026-09-26
 */
@Data
@Component
@ConfigurationProperties(prefix = "asr.alibaba")
public class AlibabaNlsAsrConfig {

    /** 阿里云NLS应用的AppKey */
    private String appKey;

    /** 阿里云AccessKey ID */
    private String accessKeyId;

    /** 阿里云AccessKey Secret */
    private String accessKeySecret;

    /** NLS服务网关URL，留空使用默认地址 */
    private String gatewayUrl = "";

    /** Token自动刷新间隔（小时），默认23小时 */
    private int tokenRefreshHours = 23;
}