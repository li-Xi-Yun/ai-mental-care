package org.lixiyun.common.core.error.exception;

import lombok.Data;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2025-12-14 14:41
 */

@Data
public class MyBatisException extends RuntimeException{

    private Integer code;
    private String msg;


    private MyBatisException() {
    }

    public MyBatisException(ErrorCode exceptionEnum) {
        super(exceptionEnum.getMsg());
        this.code = exceptionEnum.getCode();
        this.msg = exceptionEnum.getMsg();
    }

    public MyBatisException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }
}