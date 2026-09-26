package org.lixiyun.common.agent.tts.vendor.volcengine.protocol;

import lombok.Getter;

@Getter
public enum VersionBits {
    /** 版本1 */
    Version1((byte) 1),
    /** 版本2 */
    Version2((byte) 2),
    /** 版本3 */
    Version3((byte) 3),
    /** 版本4 */
    Version4((byte) 4),
    ;

    private final byte value;

    VersionBits(byte b) {
        this.value = b;
    }

    public static VersionBits fromValue(int value) {
        for (VersionBits type : VersionBits.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown VersionBits value: " + value);
    }
}