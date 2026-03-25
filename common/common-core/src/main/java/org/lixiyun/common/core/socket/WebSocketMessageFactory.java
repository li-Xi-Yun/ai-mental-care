package org.lixiyun.common.core.socket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author lixiyun
 * @since 2026-03-10 22:58
 */
@Component
public class WebSocketMessageFactory {

    @Autowired
    private Map<String, WebSocketMessageHandle> messageHandleMap;

    public WebSocketMessageHandle getMessageHandle(String messageType) {
        return messageHandleMap.get(messageType);
    }

}
