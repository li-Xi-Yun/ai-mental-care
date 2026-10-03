package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * 输出管道异常枚举
 *
 * <p>用于定义输出管道层（OutputPipeline）相关的所有业务异常</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
public enum OutputPipelineExceptionEnum implements ErrorCode {

    // 状态码从3000开始

    PIPELINE_NOT_FOUND("会话管道不存在", 3000),
    OUTPUT_TYPES_EMPTY("输出模式集合不能为空", 3001),

    OUTPUT_TYPES_NOT_FOUND("会话未绑定输出模式，无法执行", 3002),



    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    OutputPipelineExceptionEnum(String msg, int code) {
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