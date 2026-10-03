package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * 输入适配器异常枚举
 * <p>用于定义输入适配器层相关的所有业务异常</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
public enum InputAdapterExceptionEnum implements ErrorCode {

    // 状态码从2800开始

    INPUT_TYPES_EMPTY("输入方式集合不能为空", 2800),
    ADAPTER_NOT_FOUND("未找到对应类型的输入适配器", 2801),
    SESSION_NOT_FOUND("输入适配器会话不存在", 2802),
    SESSION_ALREADY_REGISTERED("输入适配器会话已被注册", 2803),
    AUDIO_DATA_EMPTY("音频数据为空", 2804),
    CONVERSATION_NOT_FOUND("会话不存在或无权访问", 2805),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    InputAdapterExceptionEnum(String msg, int code) {
        this.msg = msg;
        this.code = code;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void setCode(int code) {
        this.code = code;
    }
}