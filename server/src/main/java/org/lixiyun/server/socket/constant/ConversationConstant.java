package org.lixiyun.server.socket.constant;

/**
 * @author lixiyun
 * @date 2026/10/3 14:58
 */
public interface ConversationConstant {

    /** AI 文本回复，客户端订阅时，需要在后面路径中添加/{conversationId} */
    String AI_TEXT_REPLY = "/text/reply";

    /** 会话名称，客户端订阅时，需要在后面路径中添加/{conversationId} */
    String CONVERSATION_NAME = "/conversation/name";

    /** AI语音对话音频二进制数据流式推送，需要在后面路径中添加/{conversationId} */
    String AI_AUDIO_BINARY = "/audio/binary";

    /** ASR实时中间识别结果弹幕推送，需要在后面路径中添加/{conversationId} */
    String ASR_INTERMEDIATE_RESULT = "/audio/asr/intermediate";

    /** 待处理交互推送，需要在后面路径中添加/{conversationId} */
    String PENDING_ACTION = "/pending-action";

}