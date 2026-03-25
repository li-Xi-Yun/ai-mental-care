package org.lixiyun.common.websocket.util;

import cn.hutool.core.bean.BeanUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.common.websocket.entity.WebSocketMsg;
import org.lixiyun.common.websocket.holder.WebSocketSessionHolder;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 工具类
 *
 * @author lixiyun
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WebSocketUtils {

    // 暂不实现消息发送接收的可靠性：ack机制

    /**
     * 发送二进制音频消息（核心：原生二进制，无JSON序列化）
     *
     * @param sessionKey session主键 一般为用户id
     * @param webSocketMsg 消息数据，其中的binaryData为音频数据
     */
    public static void sendAudioMessage(Long sessionKey, WebSocketMsg<?> webSocketMsg) {
        WebSocketSession session = WebSocketSessionHolder.getSessions(sessionKey);

        // 复制对象并清空 data 字段
        WebSocketMsg<?> metadataMsg = BeanUtil.copyProperties(webSocketMsg, WebSocketMsg.class);
        metadataMsg.setBinaryData(null);

        // 将 metadata 序列化为 JSON 字符串
        String jsonStr = JsonUtils.toJsonString(metadataMsg);
        byte[] jsonBytes = jsonStr.getBytes(StandardCharsets.UTF_8);

        // 计算总长度：4 字节长度标识 + JSON 数据 + 音频数据
        byte[] audioBytes = webSocketMsg.getBinaryData();
        int totalLength = 4 + jsonBytes.length + audioBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);

        // 写入 JSON 数据的长度（4 字节，大端序）
        buffer.putInt(jsonBytes.length);

        // 写入 JSON 二进制数据
        buffer.put(jsonBytes);

        // 写入音频二进制数据
        buffer.put(audioBytes);

        // 发送二进制消息
        byte[] finalBytes = buffer.array();
        sendAudioMessage(session, finalBytes);
    }

    /**
     * 发送二进制音频消息
     * <br>
     * 固定规则-二进制消息包含三个部分：前 4 个字节 = 后续 JSON 字符串的字节长度 (int) → 接着是 JSON 二进制数据 → 最后剩余字节 = 音频数据
     *
     * @param session    WebSocket会话
     * @param audioBytes 音频二进制数据块，约定前4个字节为后续JSON对应的二进制长度，剩下的字节为音频数据
     */
    public static void sendAudioMessage(WebSocketSession session, byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            log.error("[sendAudio] 音频数据为空，无法发送");
            return;
        }
        // 直接创建二进制消息，无需序列化
        BinaryMessage binaryMessage = new BinaryMessage(audioBytes);
        sendMessage(session, binaryMessage);
        log.debug("[sendAudio] 发送音频块，长度：{}字节", audioBytes.length);
    }

    /**
     * 发送文本消息
     *
     * @param sessionKey session主键 一般为用户id
     * @param message    消息文本
     */
    public static void sendMessage(Long sessionKey, String message) {
        WebSocketSession session = WebSocketSessionHolder.getSessions(sessionKey);
        sendMessage(session, message);
    }

    /**
     * 发送WebSocketMsg对象消息
     *
     * @param sessionKey session主键 一般为用户id
     * @param webSocketMsg 自定义消息对象
     */
    public static void sendMessage(Long sessionKey, WebSocketMsg<?> webSocketMsg) {
        WebSocketSession session = WebSocketSessionHolder.getSessions(sessionKey);
        sendMessage(session, webSocketMsg);
    }

    /**
     * 发送WebSocketMsg对象（序列化后发送）
     *
     * @param session WebSocket会话
     * @param webSocketMsg 自定义消息对象
     */
    public static void sendMessage(WebSocketSession session, WebSocketMsg<?> webSocketMsg) {
        if (webSocketMsg == null) {
            log.error("[send] WebSocketMsg对象为空，无法发送");
            return;
        }
        // 将自定义消息对象序列化为JSON字符串
        String jsonMessage;
        jsonMessage = JsonUtils.toJsonString(webSocketMsg);
        // 复用原有文本消息发送逻辑
        sendMessage(session, new TextMessage(jsonMessage));
    }

    public static void sendPongMessage(WebSocketSession session) {
        sendMessage(session, new PongMessage());
    }

    public static void sendMessage(WebSocketSession session, String message) {
        sendMessage(session, new TextMessage(message));
    }

    private static void sendMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (session == null || !session.isOpen()) {
            log.error("[send] session会话已经关闭");
        } else {
            try {
                // 获取当前会话中的用户
                session.sendMessage(message);
            } catch (IOException e) {
                log.error("[send] session({}) 发送消息({}) 异常", session, message, e);
            }
        }
    }
}