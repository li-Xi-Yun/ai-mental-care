package org.lixiyun.server.infrastructure.conversation;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.websocket.utils.WebSocketUtils;
import org.lixiyun.server.socket.constant.AudioConstant;
import org.lixiyun.server.socket.constant.TextConstant;
import org.springframework.stereotype.Component;

/**
 * 会话WebSocket管理器
 * <p>
 * 负责通过WebSocket向指定用户推送会话相关消息，包括文本流式回复、会话名称和音频流式回复。
 *
 * @author lixiyun
 * @since 2026-08-17 08:42
 */
@Slf4j
@Component
public class ConversationWebSocketManager {

    /**
     * 发送文本流式回复
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param response       回复内容
     */
    public void sendTextStream(Long userId, Long conversationId, String response) {
        log.debug("会话WebSocket管理器-文本内容流式发送开始，会话ID：{}，消息长度：{}", conversationId, response.length());
        sendViaWebSocket(userId, TextConstant.AI_TEXT_REPLY, conversationId, response);
    }

    /**
     * 发送会话名称
     *
     * @param userId           目标用户ID
     * @param conversationId   会话ID
     * @param conversationName 会话名称
     */
    public void sendConversationName(Long userId, Long conversationId, String conversationName) {
        log.debug("会话WebSocket管理器-发送会话名称，会话ID：{}，名称：{}", conversationId, conversationName);
        sendViaWebSocket(userId, TextConstant.CONVERSATION_NAME, conversationId, conversationName);
    }

    /**
     * 发送音频流式回复
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param text           音频文本内容
     */
    public void sendAudioStream(Long userId, Long conversationId, String text) {
        log.debug("会话WebSocket管理器-音频内容流式发送开始，会话ID：{}，消息长度：{}", conversationId, text.length());
        sendViaWebSocket(userId, AudioConstant.AI_AUDIO_REPLY, conversationId, text);
    }

    /**
     * 发送音频二进制数据流式推送
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param audioData      音频二进制数据
     */
    public void sendAudioBinary(Long userId, Long conversationId, byte[] audioData) {
        log.debug("会话WebSocket管理器-音频二进制数据流式发送开始，会话ID：{}，数据长度：{}", conversationId, audioData.length);
        sendViaWebSocket(userId, AudioConstant.AI_AUDIO_BINARY, conversationId, audioData);
    }

    /**
     * 发送ASR实时中间识别结果（弹幕推送）
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param text           中间识别文本
     */
    public void sendAsrIntermediateResult(Long userId, Long conversationId, String text) {
        log.debug("会话WebSocket管理器-ASR中间识别结果弹幕推送，会话ID：{}，消息长度：{}", conversationId, text.length());
        sendViaWebSocket(userId, AudioConstant.ASR_INTERMEDIATE_RESULT, conversationId, text);
    }

    /**
     * 通过WebSocket发送消息
     *
     * @param userId         目标用户ID
     * @param webSocketId    WebSocket目标标识
     * @param conversationId 会话ID
     * @param response       消息内容
     */
    private void sendViaWebSocket(Long userId, String webSocketId, Long conversationId, String response) {
        WebSocketUtils.sendToUserBySubDestination(userId.toString(), webSocketId + "/" + conversationId, response);
    }

    private void sendViaWebSocket(Long userId, String webSocketId, Long conversationId, Object payload) {
        WebSocketUtils.sendToUserBySubDestination(userId.toString(), webSocketId + "/" + conversationId, payload);
    }

}