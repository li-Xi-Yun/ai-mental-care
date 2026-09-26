package org.lixiyun.common.agent.tts.vendor.volcengine.protocol;

import lombok.Getter;

@Getter
public enum MsgType {
    /** 无效消息类型 */
    INVALID((byte) 0),
    /** 完整客户端请求 */
    FULL_CLIENT_REQUEST((byte) 0b1),
    /** 仅音频客户端请求 */
    AUDIO_ONLY_CLIENT((byte) 0b10),
    /** 完整服务端响应 */
    FULL_SERVER_RESPONSE((byte) 0b1001),
    /** 仅音频服务端响应 */
    AUDIO_ONLY_SERVER((byte) 0b1011),
    /** 前端识别结果 */
    FRONT_END_RESULT_SERVER((byte) 0b1100),
    /** 错误消息 */
    ERROR((byte) 0b1111);

    private final byte value;

    MsgType(byte value) {
        this.value = value;
    }

    public static MsgType fromValue(int value) {
        for (MsgType type : MsgType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MsgType value: " + value);
    }
}