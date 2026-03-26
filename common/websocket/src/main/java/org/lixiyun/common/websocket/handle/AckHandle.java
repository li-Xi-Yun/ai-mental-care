package org.lixiyun.common.websocket.handle;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.socket.WebSocketMessageHandle;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.common.websocket.constant.WebSocketConstants;
import org.springframework.stereotype.Component;

/**
 * 实现ack机制，用于清除缓存中的消息
 * @author lixiyun
 * @since 2026-03-26 08:21
 */
@Slf4j
@Component("ack")
public class AckHandle implements WebSocketMessageHandle {

    @Override
    public void handle(Long userId, Object data) {
        RedisUtils.deleteObject(WebSocketConstants.MESSAGE_CACHE_KEY + userId);
    }

}
