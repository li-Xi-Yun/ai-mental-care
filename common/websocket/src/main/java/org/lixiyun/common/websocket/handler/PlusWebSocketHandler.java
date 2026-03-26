package org.lixiyun.common.websocket.handler;

import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.socket.WebSocketMessageHandle;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.common.websocket.entity.WebSocketMsg;
import org.lixiyun.common.websocket.enums.MessageType;
import org.lixiyun.common.websocket.holder.WebSocketSessionHolder;
import org.lixiyun.common.websocket.util.WebSocketUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * WebSocketHandler 实现类
 *
 * @author lixiyun
 */
@Slf4j
@Component
public class PlusWebSocketHandler extends AbstractWebSocketHandler {

    @Autowired
    private LocalValidatorFactoryBean validator; // Spring 校验工厂

    /**
     * 连接成功后
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSessionHolder.addSession(Long.valueOf(session.getId()), session);
        log.info("WebSocket连接建立，sessionId：{}", session.getId());
    }

    /**
     * 处理发送来的文本消息
     *
     * @param session WebSocket会话
     * @param message 文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.info("接收WebSocket的文本消息 sessionId: {}, message: {}", session.getId(), message.getPayload());
        WebSocketMsg<?> webSocketMsg = JsonUtils.parseObject(message.getPayload(), WebSocketMsg.class);

        Set<ConstraintViolation<WebSocketMsg<?>>> violations = validator.validate(webSocketMsg);

        // 处理校验结果
        if (!violations.isEmpty()) {
            // 静默处理
            log.warn("WebSocket消息校验失败：{}", violations);
            return;
        }

        try {
            // 调用对应的消息处理方法
            WebSocketMessageHandle.handle(Long.valueOf(session.getId()), webSocketMsg.getData(), webSocketMsg.getMsgType());
        } catch (NumberFormatException e) {
            log.error("WebSocket消息处理失败：{}, {}", session.getId(), e.getMessage());
        }

        // 实现ack机制，保证消息的可靠性
        WebSocketUtils.sendMessage(session, WebSocketMsg.builder()
                        .msgId(webSocketMsg.getMsgId())
                        .msgType(MessageType.ACK.getName())
                        .build());
    }

    /**
     * 处理二进制消息（音频数据块）
     * <br>
     * 固定规则：前 4 个字节 = 后续 JSON 字符串的字节长度 (int) → 接着是 JSON 二进制数据 → 最后剩余字节 = 音频数据
     * @param session WebSocket会话
    * @param message 二进制消息
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        log.info("接收WebSocket二进制消息 sessionId: {}", session.getId());
        // 1. 基础校验
        if (!session.isOpen()) {
            log.warn("WebSocket会话/消息无效，跳过处理");
            return;
        }

        String sessionId = session.getId();
        try {
            ByteBuffer payload = message.getPayload();
            // 2. 最小长度校验：必须包含前4个字节（JSON长度）
            if (payload.remaining() < 4) {
                log.warn("sessionId:{} 二进制数据长度不足，无法解析JSON长度", sessionId);
                return;
            }

            // 读取前4个字节：获取JSON字符串的字节长度
            int jsonByteLength = payload.getInt();

            // 校验数据完整性：剩余数据必须 ≥ JSON长度
            if (payload.remaining() < jsonByteLength) {
                log.warn("sessionId:{} 数据残缺，JSON需要{}字节，实际剩余{}字节",
                        sessionId, jsonByteLength, payload.remaining());
                return;
            }

            // 读取JSON二进制数据 → 转为字符串
            byte[] jsonBytes = new byte[jsonByteLength];
            payload.get(jsonBytes);
            // 用于之后的数据分类，现在不用
            WebSocketMsg<?> webSocketMsg = JsonUtils.parseObject(jsonBytes, WebSocketMsg.class);
            if(webSocketMsg == null){
                log.warn("sessionId:{} JSON解析失败", sessionId);
                return;
            }

            // 读取剩余所有数据 → 音频二进制数据
            byte[] audioData = new byte[payload.remaining()];
            payload.get(audioData);

            // 业务日志
            log.info("sessionId:{} 解析完成 | JSON长度:{}字节 | 音频数据:{}字节 | JSON内容:{}",
                    sessionId, jsonByteLength, audioData.length, webSocketMsg);
            webSocketMsg.setBinaryData(audioData);

            // 调用对应的消息处理方法
            WebSocketMessageHandle.handle(Long.valueOf(session.getId()), webSocketMsg, webSocketMsg.getMsgType());

            // 实现ack机制，保证消息的可靠性
            WebSocketUtils.sendMessage(session, WebSocketMsg.builder()
                    .msgId(webSocketMsg.getMsgId())
                    .msgType(MessageType.ACK.getName())
                    .build());
        } catch (Exception e) {
            log.error("sessionId:{} 解析二进制音频数据异常", sessionId, e);
        }
    }

    /**
     * 心跳监测的回复
     *
     * @param session WebSocket会话
     * @param message 心跳监测消息
     */
    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        WebSocketUtils.sendPongMessage(session);
    }

    /**
     * 连接出错时
     *
     * @param session WebSocket会话
     * @param exception 异常信息
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[transport error] sessionId: {} , exception:{}", session.getId(), exception.getMessage());
    }

    /**
     * 连接关闭后
     *
     * @param session WebSocket会话
     * @param status 连接状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocketSessionHolder.removeSession(Long.valueOf(session.getId()));
    }

    /**
     * 指示处理程序是否支持接收部分消息
     *
     * @return 如果支持接收部分消息，则返回true；否则返回false
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
