package org.lixiyun.common.agent.tts.vendor.volcengine.protocol;

import lombok.Getter;

@Getter
public enum HeaderSizeBits {
    /** 头部4字节 */
    HeaderSize4((byte) 1),
    /** 头部8字节 */
    HeaderSize8((byte) 2),
    /** 头部12字节 */
    HeaderSize12((byte) 3),
    /** 头部16字节 */
    HeaderSize16((byte) 4),
    ;

    private final byte value;

    HeaderSizeBits(byte b) {
        this.value = b;
    }

    public static HeaderSizeBits fromValue(int value) {
        for (HeaderSizeBits type : HeaderSizeBits.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown HeaderSizeBits value: " + value);
    }
}