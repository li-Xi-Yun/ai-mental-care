package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2026-04-28 14:56
 */
public enum FileExceptionEnum implements ErrorCode {

    // 状态码从3000开始
    FILE_NOT_FOUND("文件不存在", 3001),
    FILE_READ_ERROR("文件读取失败", 3002),
    FILE_PARSE_ERROR("文件解析失败，格式不支持或已损坏", 3003),
    NETWORK_TIMEOUT("网络请求超时", 3004),
    FILE_SIZE_EXCEEDED("文件大小超出限制", 3005),
    UNSUPPORTED_FILE_TYPE("不支持的文件类型", 3006),
    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    FileExceptionEnum(String msg, int code){
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
