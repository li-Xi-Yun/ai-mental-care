package org.lixiyun.common.agent.asr.vendor.volcengine;

import lombok.Data;
import org.lixiyun.common.json.utils.JsonUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 火山引擎ASR密钥及音频参数配置，在server模块的application.yml中配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "asr.volcengine")
public class VolcengineAsrConfig {

    // ==================== WebSocket连接配置 ====================

    /** WebSocket端点地址 */
    private String endpoint = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async";

    // ==================== 新版控制台鉴权 ====================

    /** X-Api-Key（新版控制台） */
    private String apiKey;
    /** X-Api-Resource-Id（模型版本标识） */
    private String resourceId;

    // ==================== 旧版控制台鉴权（兼容） ====================

    /** X-Api-App-Key（旧版控制台） */
    private String appKey;
    /** X-Api-Access-Key（旧版控制台） */
    private String accessKey;

    // ==================== 发送方音频参数 ====================

    /** 音频格式 */
    private String format = "pcm";
    /** 音频编码格式 */
    private String codec = "raw";
    /** 采样率 Hz */
    private int rate = 16000;
    /** 采样位深 bit */
    private int bits = 16;
    /** 声道数 */
    private int channel = 1;

    // ==================== WebSocket与连接池配置 ====================

    /** WebSocket连接超时时间（毫秒） */
    private int connectTimeoutMs = 10000;
    /** 会话空闲超时时间（毫秒），超时未被使用的会话将被自动关闭 */
    private long sessionIdleTimeoutMs = 120000;
    /** 最大并发会话数 */
    private int maxSessions = 100;
    /** 保活静音发送间隔（毫秒），0表示不发送 */
    private long keepAliveIntervalMs = 15000;

    /**
     * 将音频参数序列化为JSON字符串（仅包含audio节点的5个字段）
     *
     * @return audio节点的JSON字符串
     */
    public String toAudioJson() {
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("format", format);
        audio.put("codec", codec);
        audio.put("rate", rate);
        audio.put("bits", bits);
        audio.put("channel", channel);
        return JsonUtils.toJsonString(audio);
    }
}