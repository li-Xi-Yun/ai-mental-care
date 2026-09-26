package org.lixiyun.common.agent.tts.vendor.alibaba;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云NLS TTS配置
 * <p>绑定配置文件前缀{@code tts.alibaba}，通过{@code @ConfigurationProperties}自动注入。</p>
 *
 * @author lixiyun
 * @since 2026-09-25
 */
@Data
@Component
@ConfigurationProperties(prefix = "tts.alibaba")
public class AlibabaNlsTtsConfig {

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

    /** 发音人，默认"siyue" */
    private String voice = "siyue";

    /** 音频编码格式，支持WAV/MP3 */
    private String format;

    /** 音频采样率（Hz），如16000 */
    private int sampleRate;

    /** 音量大小，取值范围0~100，默认50 */
    private int volume = 50;

    /** 语调高低，取值范围-500~500，默认0 */
    private int pitchRate = 0;

    /** 语速快慢，取值范围-500~500，默认0 */
    private int speechRate = 0;
}