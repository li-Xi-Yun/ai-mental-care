package org.lixiyun.common.core.error.exception;

import lombok.Data;
import org.lixiyun.common.core.error.ErrorCode;


@Data
public class BusinessException extends RuntimeException{

    private Integer code;
    private String msg;


    private BusinessException() {
    }

    public BusinessException(ErrorCode exceptionEnum) {
        super(exceptionEnum.getMsg());
        this.code = exceptionEnum.getCode();
        this.msg = exceptionEnum.getMsg();
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }
}
