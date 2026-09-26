package org.lixiyun.common.agent.tts.api;

import lombok.Getter;

/**
 * TTS服务厂商类型枚举
 *
 * @author lixiyun
 * @since 2026-09-25
 */
@Getter
public enum TtsProviderType {
    /** 阿里云NLS TTS */
    ALIBABA_NLS,
    /** 火山引擎TTS */
    VOLCENGINE,
    ;
}