package org.lixiyun.server.service;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.lixiyun.pojo.dto.chat.ChatDTO;
import reactor.core.publisher.Flux;

/**
 * @author lixiyun
 * @since 2026-03-16 13:02
 */
public interface ChatService {

    Flux<String> chat(ChatDTO chatDTO) throws GraphStateException;
}
