package org.lixiyun.common.websocket.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.json.utils.JsonUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * 专门处理服务端主动推送的WebSocket消息
 * @author lixiyun
 * @since 2026-04-03 23:08
 */
@Slf4j
@Component
public class WebSocketOutboundInterceptor implements ExecutorChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // 只处理服务端推送消息
//        log.debug("WebSocket服务端消息前置拦截器-执行-服务端发送，accessor：{}，message:{}，channel:{}", accessor, message, channel);

        // SimMessageType 是spring内部所使用的通用消息类型，StompCommand 对应 STOMP 协议真实命令
        // 一个 StompCommand 对应一个 SimpMessageType，但 一个 SimpMessageType 可以对应多个 StompCommand
        if(SimpMessageType.MESSAGE.equals(accessor.getMessageType())){
            log.debug("WebSocket服务端消息前置拦截器-服务端推送执行");
            // 获取目标路径（一定能拿到）
            String destination = accessor.getDestination();
            // 获取原始用户路径
            String origDestination = accessor.getFirstNativeHeader("simpOrigDestination");
            // 获取用户
            String userId = accessor.getUser() != null ? accessor.getUser().getName() : "广播/匿名用户";
            // 获取session
            String sessionId = accessor.getSessionId();

            // 解析消息体
            Object payload = message.getPayload();
            String content;
            if (payload instanceof byte[] bytes) {
                try {
                    content = JsonUtils.parseObject(bytes, Object.class).toString();
                } catch (Exception e) {
                    content = "二进制数据[" + bytes.length + "字节]";
                }
            } else {
                content = String.valueOf(payload);
            }

            log.info("WebSocket服务端消息前置拦截器-主动推送，用户ID：{}，消息内容：{}，目标路径：{}，原始路径：{}，会话ID：{}",
                    userId, content, destination, origDestination, sessionId);
        }

        return message;
    }

}
