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

}