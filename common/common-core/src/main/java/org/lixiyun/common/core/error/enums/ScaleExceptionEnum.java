package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2026-04-15 14:39
 */
public enum ScaleExceptionEnum implements ErrorCode {

    // 状态码从1600开始

    SCALE_CATEGORY_NOT_FOUND("量表类别不存在", 1600),
    SCALE_CATEGORY_IN_USE("量表类别正在使用，无法删除", 1601),
    SCALE_NOT_FOUND("量表不存在", 1602),
    SCALE_QUESTION_NOT_FOUND("量表题目不存在", 1603),
    SCALE_OPTION_NOT_FOUND("量表选项不存在", 1604),
    SCALE_DISABLED("量表已禁用，无法答题", 1605),
    SCALE_QUESTION_OPTION_MISMATCH("题目与选项不匹配", 1606),
    SCALE_RESULT_RULE_NOT_FOUND("未找到匹配的测评结果规则", 1607),
    SCALE_NOT_DELETED("量表未被删除，无需恢复", 1608),
    SCALE_DELETED_NOT_ENABLED("量表已删除，不能启用", 1609),



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
