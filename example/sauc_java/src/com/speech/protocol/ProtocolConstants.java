package com.speech.protocol;

public final class ProtocolConstants {
    // 协议常量 - 参考Go版本的常量定义
    public static final byte PROTOCOL_VERSION = 0b0001;
    public static final byte DEFAULT_HEADER_SIZE = 0b0001;

    // 音频处理常量
    public static final int DEFAULT_SAMPLE_RATE = 16000;
    public static final int DEFAULT_BITS = 16;
    public static final int DEFAULT_CHANNELS = 1;
    public static final int DEFAULT_SEGMENT_DURATION_MS = 200;

    private ProtocolConstants() {
    }
}
