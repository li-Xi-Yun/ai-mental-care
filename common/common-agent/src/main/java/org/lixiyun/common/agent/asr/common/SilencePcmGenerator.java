package org.lixiyun.common.agent.asr.common;

/**
 * 静音PCM数据生成器
 * <p>为ASR实时语音识别长连接提供静音保活所需的静音PCM分片。
 * 当用户长时间不发言时，持续发送静音PCM可防止网关因空闲超时而断开WebSocket连接。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 生成 16kHz、16bit、单声道、20ms 的静音分片（640字节）
 * byte[] chunk = SilencePcmGenerator.generate(16000, 16, 1, 20);
 * }</pre>
 *
 * <h3>静音PCM原理</h3>
 * <p>有符号16bit PCM中，振幅值0代表静音（无声波）。因此全零字节数组即为合法静音音频。</p>
 *
 * @author lixiyun
 * @since 2026-09-26
 */
public final class SilencePcmGenerator {

    private SilencePcmGenerator() {
    }

    /**
     * 生成指定参数的静音PCM字节数组
     *
     * @param sampleRate    采样率（Hz），常用值：8000、16000
     * @param bitsPerSample 位深度，常用值：16
     * @param channels      声道数，常用值：1（单声道）
     * @param durationMs    时长（毫秒），常用值：20（与音频采集帧对齐）
     * @return 全零静音PCM字节数组
     */
    public static byte[] generate(int sampleRate, int bitsPerSample, int channels, int durationMs) {
        int bytesPerSample = bitsPerSample / 8;
        int samplesPerChannel = sampleRate * durationMs / 1000;
        int totalBytes = samplesPerChannel * channels * bytesPerSample;
        return new byte[totalBytes]; // Java new byte[] 默认全零 = PCM静音
    }
}