package org.lixiyun.server.socket.handle;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.socket.WebSocketMessageHandle;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-03-23 20:39
 */
@Slf4j
@Component("audioStop")
public class AudioStopHandle implements WebSocketMessageHandle {

    @Override
    public void handle(Long userId, Object data) {
        log.info("音频功能结束：{}", userId);
//        AudioCache.clearAudioCache(userId);
    }
}
