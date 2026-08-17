package org.lixiyun.server.socket.constant;

/**
 * @author lixiyun
 * @since 2026-04-03 15:02
 */
public interface AudioConstant {

    String AUDIO_CONVERSATION_ID = "/audio/conversationId";

    String AUDIO_ROUND = "/audio/round";

    /** AI语音对话文本流式返回，需要在后面路径中添加/{conversationId} */
    String AI_AUDIO_REPLY = "/audio/reply";

    /** AI语音对话音频二进制数据流式推送，需要在后面路径中添加/{conversationId} */
    String AI_AUDIO_BINARY = "/audio/binary";

    /** ASR实时中间识别结果弹幕推送，需要在后面路径中添加/{conversationId} */
    String ASR_INTERMEDIATE_RESULT = "/audio/asr/intermediate";

}