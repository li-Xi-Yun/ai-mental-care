package org.lixiyun.common.agent.asr.vendor.volcengine.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 火山引擎ASR压缩类型枚举
 */
@Getter
@AllArgsConstructor
public enum CompressionType {

    /** 无压缩 */
    NO_COMPRESSION(0x00),
    /** GZIP压缩 */
    GZIP(0x01);

    private final int code;
}