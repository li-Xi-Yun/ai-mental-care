package org.lixiyun.common.websocket.holder;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocketSession 用于保存当前所有在线的会话信息
 *
 * @author lixiyun
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WebSocketSessionHolder {

    // key是用户ID，value是对应的WebSocketSession
    private static final Map<Long, WebSocketSession> USER_SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * 添加会话
     *
     * @param sessionKey session主键 一般为用户id
     * @param session    WebSocket会话
     */
    public static void addSession(Long sessionKey, WebSocketSession session) {
        USER_SESSION_MAP.put(sessionKey, session);
    }

    /**
     * 移除会话
     *
     * @param sessionKey session主键 一般为用户id
     */
    public static void removeSession(Long sessionKey) {
        if (USER_SESSION_MAP.containsKey(sessionKey)) {
            USER_SESSION_MAP.remove(sessionKey);
        }
    }

    /**
     * 获取会话
     *
     * @param sessionKey session主键 一般为用户id
     * @return WebSocketSession
     */
    public static WebSocketSession getSessions(Long sessionKey) {
        return USER_SESSION_MAP.get(sessionKey);
    }

    /**
     * 判断会话是否存在
     *
     * @param sessionKey session主键 一般为用户id
     * @return true/false
     */
    public static Boolean existSession(Long sessionKey) {
        return USER_SESSION_MAP.containsKey(sessionKey);
    }

}
