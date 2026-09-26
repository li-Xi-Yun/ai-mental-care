package org.lixiyun.common.agent.tts.vendor.volcengine;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山引擎TTS配置
 * <p>绑定配置文件前缀{@code tts.volcengine}，通过{@code @ConfigurationProperties}自动注入。</p>
 *
 * @author lixiyun
 * @since 2026-09-25
 */
@Data
@Component
@ConfigurationProperties(prefix = "tts.volcengine")
public class VolcengineTtsConfig {

    /** 火山引擎API Key（新版控制台鉴权） */
    private String apiKey;

    /** 资源ID，决定模型版本和计费方式，如 seed-tts-2.0 */
    private String resourceId;

    /** WebSocket服务端点，默认双向流式地址 */
    private String endpoint = "wss://openspeech.bytedance.com/api/v3/tts/bidirection";

    /** 发音人，见火山引擎音色列表 */
    private String speaker;

    /** 音频编码格式，支持mp3/ogg_opus/pcm，默认mp3 */
    private String format = "mp3";

    /** 音频采样率（Hz），默认24000 */
    private int sampleRate = 24000;

    /** 语速，取值范围[-50,100]，默认0 */
    private int speechRate = 0;

    /** 音量，取值范围[-50,100]，默认0 */
    private int loudnessRate = 0;
}