package org.lixiyun.server.socket.handle;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.socket.WebSocketMessageHandle;
import org.lixiyun.server.socket.cache.TtsStreamHolder;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-03-25 20:29
 */
@Slf4j
@Component("audioStreamInterrupt")
public class AudioStreamInterrupt implements WebSocketMessageHandle {

    @Override
    public void handle(Long userId, Object data) {
        // 中断音频流式响应
        TtsStreamHolder.interrupt(userId);
    }

}
