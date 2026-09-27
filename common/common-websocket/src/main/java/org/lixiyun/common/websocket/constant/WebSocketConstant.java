package org.lixiyun.common.websocket.constant;

/**
 * WebSocket 常量
 *
 * @author lixiyun
 */
public class WebSocketConstant {

    /**
     * WebSocket 握手阶段存储在 Session 属性中的用户认证信息 Key，
     * Spring 框架约定名称，用于后续 STOMP 帧中通过 {@code accessor.getUser()} 自动获取
     */
    public static final String SIMP_USER_KEY = "simpUser";

    /**
     * 一次性 WS Ticket 的 Redis Key 前缀
     */
    public static final String WS_TICKET_KEY_PREFIX = "ws:ticket:";

}