package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2026-04-15 14:39
 */
public enum ScaleExceptionEnum implements ErrorCode {


    SCALE_CATEGORY_NOT_FOUND("量表类别不存在", 4000),
    SCALE_CATEGORY_IN_USE("量表类别正在使用，无法删除", 4001),
    SCALE_NOT_FOUND("量表不存在", 4002),
    SCALE_QUESTION_NOT_FOUND("量表题目不存在", 4003),
    SCALE_OPTION_NOT_FOUND("量表选项不存在", 4004),
    SCALE_DISABLED("量表已禁用，无法答题", 4005),
    SCALE_QUESTION_OPTION_MISMATCH("题目与选项不匹配", 4006),
    SCALE_RESULT_RULE_NOT_FOUND("未找到匹配的测评结果规则", 4007),



    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    ScaleExceptionEnum(String msg, int code){
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
