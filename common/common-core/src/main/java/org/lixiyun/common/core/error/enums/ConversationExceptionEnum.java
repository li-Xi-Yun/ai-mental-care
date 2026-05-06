package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2026-03-16 17:33
 */
public enum ConversationExceptionEnum implements ErrorCode {
    // 状态码从2000开始

    CONVERSATION_NOT_EXIST("会话不存在", 3000),
    CONVERSATION_PARAM_ERROR("会话参数错误", 3001),
    CONVERSATION_NOT_FOUND("会话未找到", 3002),
    MODEL_NOT_EXIST("模型不存在", 3003),
    CONVERSATION_NAME_EXTRACTION_ERROR("会话名称提取错误", 3004),
    SEMANTIC_COMPRESSION_ERROR("语义压缩错误", 3005),
    CONVERSATION_METADATA_NOT_CONFIGURED("未配置对话元数据信息", 3006),
    DIALOGUE_NOT_EXIST("对话不存在", 3007),
    EMOTION_ANALYSIS_NOT_EXIST("情绪分析不存在", 3008),

    AUDIO_CACHE_NOT_INITIALIZED("未初始化音频缓存", 3009),
    AUDIO_CACHE_WRITE_FAILED("音频缓存写入失败", 3010),
    AUDIO_CACHE_CLOSE_FAILED("音频缓存关闭失败", 3011),
    AUDIO_DATA_NOT_EXIST("模型调用中，音频数据不存在", 3012),
    AUDIO_TEXT_DATA_NOT_EXIST("模型调用中，文本数据不存在", 3013),
    CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS("不能同时有多个语音对话", 3014),
    RAG_PARAM_MISSING("RAG流程参数缺失", 3015),

    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    ConversationExceptionEnum(String msg, int code){
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
