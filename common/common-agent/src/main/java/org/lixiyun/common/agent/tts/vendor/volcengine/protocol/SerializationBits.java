package org.lixiyun.common.agent.tts.vendor.volcengine.protocol;

import lombok.Getter;

@Getter
public enum SerializationBits {
    /** 原始序列化（无序列化） */
    Raw((byte) 0),
    /** JSON序列化 */
    JSON((byte) 0b1),
    /** Thrift序列化 */
    Thrift((byte) 0b11),
    /** 自定义序列化 */
    Custom((byte) 0b1111),
    ;

    private final byte value;

    SerializationBits(byte b) {
        this.value = b;
    }

    public static SerializationBits fromValue(int value) {
        for (SerializationBits type : SerializationBits.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SerializationBits value: " + value);
    }
}