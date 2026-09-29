package org.lixiyun.common.agent.asr.vendor.volcengine.protocol;

/**
 * 火山引擎ASR协议常量
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
    }

    /** 协议版本号 */
    public static final int PROTOCOL_VERSION = 0x01;
    /** 固定头部长度（4字节） */
    public static final int DEFAULT_HEADER_SIZE = 0x01;
    /** 默认音频分片时长（毫秒） */
    public static final int DEFAULT_SEGMENT_DURATION_MS = 200;
    /** 单个分片最大字节数（16kHz/16bit/单声道 200ms ≈ 6400字节） */
    public static final int MAX_SEGMENT_SIZE_BYTES = 6400;
    /** 音频格式：PCM */
    public static final String AUDIO_FORMAT_PCM = "pcm";
    /** 音频编码：原始无压缩 */
    public static final String AUDIO_CODEC_RAW = "raw";
}