package org.lixiyun.common.agent.asr.api;

import lombok.Getter;

/**
 * ASR服务厂商类型枚举
 *
 * @author lixiyun
 * @since 2026-09-26
 */
@Getter
public enum AsrProviderType {
    /** 阿里云NLS ASR */
    ALIBABA_NLS,
    /** 火山引擎ASR */
    VOLCENGINE,
    ;
}