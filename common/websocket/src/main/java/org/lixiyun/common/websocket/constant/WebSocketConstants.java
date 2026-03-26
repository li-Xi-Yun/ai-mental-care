package org.lixiyun.common.websocket.constant;

/**
 * websocket的常量配置
 *
 * @author lixiyun
 */
public interface WebSocketConstants {
    /**
     * websocketSession中的参数的key
     */
    String LOGIN_USER_KEY = "loginUser";

    /**
     * 订阅的频道
     */
    String WEB_SOCKET_TOPIC = "global:websocket";

    /**
     * 前端心跳检查的命令
     */
    String PING = "ping";

    /**
     * 服务端心跳恢复的字符串
     */
    String PONG = "pong";

    /**
     * 消息缓存的key，用于实现ack机制
     */
    String MESSAGE_CACHE_KEY = "websocket:message:";

    /**
     * 消息缓存的过期时间，单位为秒
     */
    int MESSAGE_CACHE_TIMEOUT = 60;
}
