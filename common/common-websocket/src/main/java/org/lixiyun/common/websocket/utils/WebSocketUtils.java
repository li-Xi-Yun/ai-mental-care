package org.lixiyun.common.websocket.utils;

import org.lixiyun.common.core.utils.SpringUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * @author lixiyun
 * @since 2026-03-29 19:40
 */
public class WebSocketUtils {

    private static final SimpMessagingTemplate template = SpringUtils.getBean(SimpMessagingTemplate.class);

    // ======================== 广播推送（所有在线客户端都能收到） ========================

    /**
     * 向所有在线客户端广播消息（自动序列化 JSON），基路径为 /topic
     * @param subDestination 订阅子主题
     * @param payload 消息对象
     */
    public static void messageBroadcastBySubDestination(String subDestination, Object payload){
        String  destination = "/topic" + subDestination;
        messageBroadcastByAnyTheme(destination, payload);
    }

    // ======================== 点对点推送（指定用户） ========================

    /**
     * 向指定用户发送消息（自动序列化 JSON），基路径为 /queue
     * @param userId 用户ID
     * @param subDestination 订阅子主题
     * @param payload 消息对象
     */
    public static void sendToUserBySubDestination(String userId, String subDestination, Object payload){
        String  destination = "/queue" + subDestination;
        sendToUserByAnyTheme(userId, destination, payload);
    }


    // ======================== 指定主题推送（自定义频道） ========================
    /**
     * 向指定主题广播消息（自动序列化 JSON）
     * @param destination 订阅主题
     * @param payload 消息对象
     */
    public static void messageBroadcastByAnyTheme(String destination, Object payload) {
        template.convertAndSend(destination, payload);
    }

    /**
     * 向指定主题与指定用户发送消息（自动序列化 JSON）
     * @param userId 用户ID
     * @param destination 订阅主题
     * @param payload 消息对象
     */
    public static void sendToUserByAnyTheme(String userId, String destination, Object payload){
        template.convertAndSendToUser(userId, destination, payload);
    }

}
