package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2025-12-13 13:11
 */
public enum SystemExceptionEnum implements ErrorCode {

    SYSTEM_ERROR("系统错误", 10000),
    FREQUENT_OPERATION("频繁操作", 10001),

    ILLEGAL_CONDITION("非法条件异常", 10002),
    PARAM_ILLEGAL("参数非法", 10003),
    PARAM_ERROR("参数错误", 10004),


    NO_PERMISSION("权限不足", 10005),

    FILE_UPLOAD_ERROR("文件上传错误", 10006),
    FILE_DATA_EMPTY("文件数据不能为空", 10007),
    FILE_EXTENTION_ERROR("文件后缀名错误", 10008),



    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    SystemExceptionEnum(String msg, int code){
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
