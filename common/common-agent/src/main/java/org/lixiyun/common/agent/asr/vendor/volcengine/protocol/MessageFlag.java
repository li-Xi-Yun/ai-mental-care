package org.lixiyun.common.agent.asr.vendor.volcengine.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 火山引擎ASR消息标志位枚举，用于指示消息中是否携带序号及是否为最后一个包
 */
@Getter
@AllArgsConstructor
public enum MessageFlag {

    /** 无序列号标志 */
    NO_SEQUENCE(0x00),
    /** 正常序列号（非最后一个包） */
    POS_SEQUENCE(0x01),
    /** 负序列号（最后一个包，二进制模式下不使用） */
    NEG_SEQUENCE(0x02),
    /** 带序列号的最后一个包 */
    NEG_WITH_SEQUENCE(0x03);

    private final int code;
}