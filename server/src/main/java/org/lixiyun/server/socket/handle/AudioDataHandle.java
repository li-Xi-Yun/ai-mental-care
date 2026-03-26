package org.lixiyun.server.socket.handle;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.socket.WebSocketMessageHandle;
import org.lixiyun.common.websocket.cache.AudioCache;
import org.lixiyun.common.websocket.entity.WebSocketMsg;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-03-24 12:53
 */
@Slf4j
@Component("audioData")
public class AudioDataHandle implements WebSocketMessageHandle {

    @Override
    public void handle(Long userId, Object data) {
        // 存储音频数据（修复：sessionId为String，直接使用）
        AudioCache.addAudioData(userId, ((WebSocketMsg<?>) data).getBinaryData());
    }

}
