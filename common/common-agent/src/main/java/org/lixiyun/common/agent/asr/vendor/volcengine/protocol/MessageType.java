package org.lixiyun.common.agent.asr.vendor.volcengine.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 火山引擎ASR消息类型枚举
 */
@Getter
@AllArgsConstructor
public enum MessageType {

    /** 客户端完整的全功能请求（启动识别） */
    CLIENT_FULL_REQUEST(0x01),
    /** 客户端音频数据请求 */
    CLIENT_AUDIO_ONLY_REQUEST(0x02),
    /** 服务端完整的全功能响应 */
    SERVER_FULL_RESPONSE(0x09),
    /** 服务端错误响应 */
    SERVER_ERROR_RESPONSE(0x0F);

    private final int code;

    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}