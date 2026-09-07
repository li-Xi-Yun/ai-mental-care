package org.lixiyun.server.properity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TTS语音合成配置属性
 *
 * @author lixiyun
 * @since 2026-08-16
 */
@Data
@Component
@ConfigurationProperties(prefix = "tts.nls")
public class TtsProperties {

    /**
     * 阿里云NLS应用AppKey
     * <p>在阿里云智能语音交互控制台创建应用后获取</p>
     */
    private String appKey;

    /**
     * 阿里云AccessKey ID
     * <p>用于通过AccessToken获取NLS服务访问Token</p>
     */
    private String accessKeyId;

    /**
     * 阿里云AccessKey Secret
     * <p>与accessKeyId配对使用，用于Token获取</p>
     */
    private String accessKeySecret;

    /**
     * NLS网关URL
     * <p>默认为空时使用阿里云线上默认地址（wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1），
     * 可自定义指定其他区域网关</p>
     */
    private String gatewayUrl = "";

    /**
     * Token自动刷新间隔（小时）
     * <p>阿里云Token有效期24小时，默认23小时刷新一次以确保Token始终有效</p>
     */
    private int tokenRefreshHours = 23;

    /**
     * 发音人标识
     * <p>可选值参见阿里云CosyVoice音色列表，不同模型版本对应不同音色</p>
     */
    private String voice = "siyue";

    /**
     * 输出音频格式, 支持WAV和MP3格式
     */
    private String format;

    /**
     * 采样率，支持8K、16K、24K、48K采样率
     */
    private int sampleRate;

    /**
     * 音量，范围0~100
     */
    private int volume = 50;

    /**
     * 语调，范围-500~500
     */
    private int pitchRate = 0;

    /**
     * 语速，范围-500~500
     */
    private int speechRate = 0;
}