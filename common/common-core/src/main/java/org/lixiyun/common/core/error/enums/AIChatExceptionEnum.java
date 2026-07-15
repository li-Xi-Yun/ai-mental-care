package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * AI聊天模块异常枚举
 * <p>用于定义AI聊天相关的所有业务异常</p>
 *
 * @author lixiyun
 * @since 2026-07-14 16:30
 */
public enum AIChatExceptionEnum implements ErrorCode {

    // 状态码从2200开始

    USER_MESSAGE_EMPTY("用户消息不能为空", 2201),
    CONVERSATION_ID_INVALID("会话ID无效", 2202),
    CONVERSATION_STATUS_ABNORMAL("会话状态异常", 2203),
    CONVERSATION_CACHE_NOT_FOUND("会话缓存数据不存在", 2204),
    MESSAGE_SAVE_FAILED("消息保存失败", 2205),
    REDIS_OPERATION_FAILED("Redis操作失败", 2206),
    LLM_CALL_FAILED("LLM调用失败", 2207),
    WEBSOCKET_SEND_FAILED("WebSocket消息发送失败", 2208),
    SEMANTIC_COMPRESSION_NOT_IMPLEMENTED("语义压缩功能暂未实现", 2209),
    ANALYSIS_DIAGNOSIS_NOT_IMPLEMENTED("分析诊断功能暂未实现", 2210),
    CONVERSATION_NOT_FOUND("会话不存在", 2211),
    PROCESSOR_NOT_FOUND("消息处理器未找到", 2212),
    MAIN_THREAD_EXECUTION_FAILED("主线程执行失败", 2213),
    TEMPORARY_MESSAGES_EMPTY("临时消息为空", 2214),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    AIChatExceptionEnum(String msg, int code){
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