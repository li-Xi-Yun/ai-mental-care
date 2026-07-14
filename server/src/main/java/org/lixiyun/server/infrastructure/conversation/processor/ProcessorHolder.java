package org.lixiyun.server.infrastructure.conversation.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lixiyun
 * @since 2026-07-14 21:40
 */
@Slf4j
@Component
public class ProcessorHolder {

    @Autowired
    private final Map<String, MessageProcessor> processors = new HashMap<>();

    public MessageProcessor getProcessor(String type) {
        return processors.get(type);
    }
}
