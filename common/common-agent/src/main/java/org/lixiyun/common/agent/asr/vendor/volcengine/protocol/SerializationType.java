package org.lixiyun.common.agent.asr.vendor.volcengine.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 火山引擎ASR序列化类型枚举
 */
@Getter
@AllArgsConstructor
public enum SerializationType {

    /** 无序列化（原始字节） */
    NO_SERIALIZATION(0x00),
    /** JSON序列化 */
    JSON(0x01);

    private final int code;
}