package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2026-03-16 17:33
 */
public enum ConversationExceptionEnum implements ErrorCode {
    // 状态码从1400开始

    CONVERSATION_NOT_EXIST("会话不存在", 1400),
    CONVERSATION_PARAM_ERROR("会话参数错误", 1401),
    CONVERSATION_NOT_FOUND("会话未找到", 1402),
    MODEL_NOT_EXIST("模型不存在", 1403),
    CONVERSATION_NAME_EXTRACTION_ERROR("会话名称提取错误", 1404),
    SEMANTIC_COMPRESSION_ERROR("语义压缩错误", 1405),
    CONVERSATION_METADATA_NOT_CONFIGURED("未配置对话元数据信息", 1406),
    DIALOGUE_NOT_EXIST("对话不存在", 1407),
    EMOTION_ANALYSIS_NOT_EXIST("情绪分析不存在", 1408),

    AUDIO_CACHE_NOT_INITIALIZED("未初始化音频缓存", 1409),
    AUDIO_CACHE_WRITE_FAILED("音频缓存写入失败", 1410),
    AUDIO_CACHE_CLOSE_FAILED("音频缓存关闭失败", 1411),
    AUDIO_DATA_NOT_EXIST("模型调用中，音频数据不存在", 1412),
    AUDIO_TEXT_DATA_NOT_EXIST("模型调用中，文本数据不存在", 1413),
    CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS("不能同时有多个语音对话", 1414),
    RAG_PARAM_MISSING("RAG流程参数缺失", 1415),

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
