package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2026-03-16 17:33
 */
public enum ConversationException implements ErrorCode {
    // 状态码从3000开始

    CONVERSATION_NOT_EXIST("会话不存在", 3000),
    CONVERSATION_PARAM_ERROR("会话参数错误", 3001),
    CONVERSATION_NOT_FOUND("会话未找到", 3002),



    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    ConversationException(String msg, int code){
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
