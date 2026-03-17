package org.lixiyun.common.sql.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2025-12-14 14:38
 */
public enum MyBatisExceptionEnum implements ErrorCode {

    SQL_ERROR("数据库异常", 2000),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    MyBatisExceptionEnum(String msg, int code){
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
