package org.lixiyun.common.agent.tts.vendor.volcengine.protocol;

import lombok.Getter;

@Getter
public enum MsgTypeFlagBits {
    /** 无序列号 */
    NO_SEQ((byte) 0),
    /** 正数序列号 */
    POSITIVE_SEQ((byte) 0b1),
    /** 最后一条消息，无序列号 */
    LAST_NO_SEQ((byte) 0b10),
    /** 负数序列号 */
    NEGATIVE_SEQ((byte) 0b11),
    /** 携带事件类型 */
    WITH_EVENT((byte) 0b100);

    private final byte value;

    MsgTypeFlagBits(byte value) {
        this.value = value;
    }

    public static MsgTypeFlagBits fromValue(int value) {
        for (MsgTypeFlagBits flag : MsgTypeFlagBits.values()) {
            if (flag.value == value) {
                return flag;
            }
        }
        throw new IllegalArgumentException("Unknown MsgTypeFlagBits value: " + value);
    }
}