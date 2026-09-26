package org.lixiyun.common.agent.tts.vendor.volcengine;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.lixiyun.common.agent.tts.vendor.volcengine.protocol.EventType;
import org.lixiyun.common.agent.tts.vendor.volcengine.protocol.Message;
import org.lixiyun.common.agent.tts.vendor.volcengine.protocol.MsgType;
import org.lixiyun.common.agent.tts.vendor.volcengine.protocol.MsgTypeFlagBits;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 火山引擎TTS WebSocket客户端
 * <p>封装与火山引擎双向流式TTS服务的WebSocket长连接，支持自定义鉴权头、
 * 二进制协议帧收发和阻塞式消息队列。</p>
 *
 * @author lixiyun
 * @since 2026-09-25
 */
@Slf4j
public class VolcWebSocketClient extends WebSocketClient {

    /** 消息接收队列，生产者=onMessage线程，消费者=Dispatcher */
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();

    /** 连接建立成功的信号量 */
    private volatile boolean connected = false;

    /**
     * 构造WebSocket客户端
     *
     * @param serverUri 服务端URI
     * @param headers   鉴权请求头
     */
    public VolcWebSocketClient(URI serverUri, Map<String, String> headers) {
        super(serverUri, headers);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        connected = true;
        log.info("[火山引擎 TTS] WebSocket连接建立，LogId: {}",
                handshake.getFieldValue("x-tt-logid"));
    }

    @Override
    public void onMessage(String message) {
        log.warn("[火山引擎 TTS] 收到非预期的文本消息: {}", message);
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            Message message = Message.unmarshal(bytes.array());
            messageQueue.put(message);
        } catch (Exception e) {
            log.error("[火山引擎 TTS] 二进制消息解析失败", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        connected = false;
        log.info("[火山引擎 TTS] WebSocket连接关闭: code={}, reason={}, remote={}", code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        connected = false;
        log.error("[火山引擎 TTS] WebSocket连接异常", ex);
    }

    /** @return 连接是否已建立 */
    public boolean isConnected() {
        return connected && isOpen();
    }

    /** @return 阻塞获取下一条消息 */
    public Message receiveMessage() throws InterruptedException {
        return messageQueue.take();
    }

    /** @return 带超时的阻塞获取下一条消息 */
    public Message receiveMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }

    /**
     * 等待指定类型和事件的响应消息
     *
     * @param type  期望的消息类型
     * @param event 期望的事件类型
     * @return 匹配的消息
     * @throws RuntimeException 收到非预期消息时抛出
     */
    public Message waitForMessage(MsgType type, EventType event) throws InterruptedException {
        while (true) {
            Message msg = receiveMessage();
            if (msg.getType() == type && msg.getEvent() == event) {
                return msg;
            }
            if (msg.getType() == MsgType.ERROR) {
                String errorPayload = msg.getPayload() != null ? new String(msg.getPayload()) : "unknown";
                throw new RuntimeException(
                        "[火山引擎 TTS] 收到错误响应: code=" + msg.getErrorCode() + ", payload=" + errorPayload);
            }
            throw new RuntimeException(
                    "[火山引擎 TTS] 收到非预期消息: type=" + msg.getType() + ", event=" + msg.getEvent());
        }
    }

    // ======================== 连接管理 ========================

    /** 发送StartConnection帧 */
    public void sendStartConnection() throws Exception {
        Message message = new Message(MsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.START_CONNECTION);
        message.setPayload("{}".getBytes());
        sendFrame(message);
    }

    /** 发送FinishConnection帧 */
    public void sendFinishConnection() throws Exception {
        Message message = new Message(MsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.FINISH_CONNECTION);
        sendFrame(message);
    }

    // ======================== 会话管理 ========================

    /**
     * 发送StartSession帧，携带TTS参数
     *
     * @param payload   JSON格式的TTS请求参数
     * @param sessionId 会话唯一标识
     */
    public void sendStartSession(byte[] payload, String sessionId) throws Exception {
        Message message = new Message(MsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.START_SESSION);
        message.setSessionId(sessionId);
        message.setPayload(payload);
        sendFrame(message);
    }

    /**
     * 发送FinishSession帧
     *
     * @param sessionId 会话唯一标识
     */
    public void sendFinishSession(String sessionId) throws Exception {
        Message message = new Message(MsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.FINISH_SESSION);
        message.setSessionId(sessionId);
        message.setPayload("{}".getBytes());
        sendFrame(message);
    }

    /**
     * 发送CancelSession帧
     *
     * @param sessionId 会话唯一标识
     */
    public void sendCancelSession(String sessionId) throws Exception {
        Message message = new Message(MsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.CANCEL_SESSION);
        message.setSessionId(sessionId);
        message.setPayload("{}".getBytes());
        sendFrame(message);
    }

    // ======================== 数据传输 ========================

    /**
     * 发送TaskRequest帧，携带合成文本
     *
     * @param payload   JSON格式的文本请求参数
     * @param sessionId 会话唯一标识
     */
    public void sendTaskRequest(byte[] payload, String sessionId) throws Exception {
        Message message = new Message(MsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.TASK_REQUEST);
        message.setSessionId(sessionId);
        message.setPayload(payload);
        sendFrame(message);
    }

    // ======================== 内部方法 ========================

    /** 序列化并发送二进制帧 */
    private void sendFrame(Message message) throws Exception {
        log.debug("[火山引擎 TTS] 发送: {}", message);
        send(message.marshal());
    }
}