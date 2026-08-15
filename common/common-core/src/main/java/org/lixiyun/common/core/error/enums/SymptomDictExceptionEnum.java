package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * 症状词典异常枚举
 *
 * @author lixiyun
 * @since 2026-08-15
 */
public enum SymptomDictExceptionEnum implements ErrorCode {

    // 状态码从2400开始

    SYMPTOM_DICT_NOT_FOUND("症状词典不存在", 2400),
    SYMPTOM_DICT_TERM_EXISTS("标准症状名称已存在", 2401),
    ADD_SYMPTOM_DICT_FAIL("症状词典新增失败", 2402),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    SymptomDictExceptionEnum(String msg, int code) {
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