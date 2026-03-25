package org.lixiyun.server.socket.handle;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.socket.WebSocketMessageHandle;
import org.lixiyun.common.websocket.cache.AudioCache;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-03-23 20:38
 */
@Slf4j
@Component("audioStart")
public class AudioStartHandle implements WebSocketMessageHandle {

    @Override
    public void handle(Long userId, Object data) {
        log.info("开始初始化音频数据缓存区：{}", userId);
        AudioCache.initAudioCache(userId);
    }
}
