package org.lixiyun.server.infrastructure.conversation;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.websocket.utils.WebSocketUtils;
import org.lixiyun.pojo.entity.conversation.ConversationPendingAction;
import org.lixiyun.server.socket.constant.AudioConstant;
import org.lixiyun.server.socket.constant.AvatarConstant;
import org.lixiyun.server.socket.constant.ConversationConstant;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        sendViaWebSocket(userId, ConversationConstant.AI_TEXT_REPLY, conversationId, response);
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
        sendViaWebSocket(userId, ConversationConstant.CONVERSATION_NAME, conversationId, conversationName);
    }

    /**
     * 发送音频流式回复
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param text           音频文本内容
     */
    @Deprecated
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
        sendViaWebSocket(userId, ConversationConstant.AI_AUDIO_BINARY, conversationId, audioData);
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
        sendViaWebSocket(userId, ConversationConstant.ASR_INTERMEDIATE_RESULT, conversationId, text);
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
        log.debug("[WebSocket] 发送消息，用户ID：{}，目标：{}/{}，消息长度：{}", userId, webSocketId, conversationId, response != null ? response.length() : 0);
        WebSocketUtils.sendToUserBySubDestination(userId.toString(), webSocketId + "/" + conversationId, response);
    }

    /**
     * 通过WebSocket发送消息（Object类型）
     *
     * @param userId         目标用户ID
     * @param webSocketId    WebSocket目标标识
     * @param conversationId 会话ID
     * @param payload        消息内容（Object类型）
     */
    private void sendViaWebSocket(Long userId, String webSocketId, Long conversationId, Object payload) {
        log.debug("[WebSocket] 发送消息(Object)，用户ID：{}，目标：{}/{}，payload类型：{}", userId, webSocketId, conversationId, payload != null ? payload.getClass().getSimpleName() : "null");
        WebSocketUtils.sendToUserBySubDestination(userId.toString(), webSocketId + "/" + conversationId, payload);
    }

    /**
     * 发送待处理交互推送
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param pendingAction  待处理交互记录
     */
    public void sendPendingAction(Long userId, Long conversationId, ConversationPendingAction pendingAction) {
        sendViaWebSocket(userId, ConversationConstant.PENDING_ACTION, conversationId, pendingAction);
    }

    /**
     * 发送虚拟形象表情指令（持续状态，切换后保持直到下次切换）
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param expression     表情资产 id（来自 expressionList）
     */
    public void sendAvatarExpression(Long userId, Long conversationId, String expression) {
        log.debug("会话WebSocket管理器-虚拟形象表情指令下发，会话ID：{}，表情：{}", conversationId, expression);
        sendViaWebSocket(userId, AvatarConstant.AI_AVATAR_EXPRESSION, conversationId, Map.of("expression", expression));
    }

    /**
     * 发送虚拟形象动作指令（一次性触发，播放完毕后自动回归）
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param motion         动作资产 id（来自 motionList）
     */
    public void sendAvatarMotion(Long userId, Long conversationId, String motion) {
        log.debug("会话WebSocket管理器-虚拟形象动作指令下发，会话ID：{}，动作：{}", conversationId, motion);
        sendViaWebSocket(userId, AvatarConstant.AI_AVATAR_MOTION, conversationId, Map.of("motion", motion));
    }

    /**
     * 发送AI配额拒绝事件（异步资源超限/停用时由 QuotaLimitAspect 调用）
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param resourceType   资源类型：2=ASR 3=TTS 4=AVATAR_2D
     */
    public void sendQuotaRejected(Long userId, Long conversationId, Integer resourceType) {
        log.warn("会话WebSocket管理器-配额拒绝事件下发，会话ID：{}，资源类型：{}", conversationId, resourceType);
        sendViaWebSocket(userId, ConversationConstant.QUOTA_REJECTED, conversationId,
                Map.of("event", "QUOTA_REJECTED", "resourceType", resourceType));
    }

    /**
     * 发送新情绪分析落库通知（EmotionRecognitionNode 插入 emotion_analysis 后调用）
     * <p>载荷为轻量事件：{event, id, roundNum}，前端收到后复用既有 REST 列表接口拉取权威数据。</p>
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param analysisId     新情绪分析ID（雪花 Long → 字符串，避免前端精度丢失）
     * @param roundNum       当前轮次
     */
    public void sendEmotionAnalysisUpdated(Long userId, Long conversationId, Long analysisId, Integer roundNum) {
        log.info("会话WebSocket管理器-情绪分析落库通知下发，会话ID：{}，分析ID：{}，轮次：{}", conversationId, analysisId, roundNum);
        sendViaWebSocket(userId, ConversationConstant.EMOTION_ANALYSIS_UPDATED, conversationId,
                Map.of("event", "EMOTION_ANALYSIS_UPDATED", "id", String.valueOf(analysisId), "roundNum", roundNum));
    }

    /**
     * 发送新心理评估（诊断书）落库通知（DiagnosisPersistNode 插入 emotion_diagnosis 后调用）
     * <p>载荷为轻量事件：{event, id, roundNum}，前端收到后复用既有 REST 详情/列表接口拉取权威数据。</p>
     *
     * @param userId         目标用户ID
     * @param conversationId 会话ID
     * @param diagnosisId    新诊断书ID（雪花 Long → 字符串，避免前端精度丢失）
     * @param roundNum       创建该诊断时的会话轮次
     */
    public void sendDiagnosisCreated(Long userId, Long conversationId, Long diagnosisId, Integer roundNum) {
        log.info("会话WebSocket管理器-心理评估落库通知下发，会话ID：{}，诊断ID：{}，轮次：{}", conversationId, diagnosisId, roundNum);
        sendViaWebSocket(userId, ConversationConstant.DIAGNOSIS_CREATED, conversationId,
                Map.of("event", "DIAGNOSIS_CREATED", "id", String.valueOf(diagnosisId), "roundNum", roundNum));
    }
}