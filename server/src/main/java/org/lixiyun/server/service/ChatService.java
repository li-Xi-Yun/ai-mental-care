package org.lixiyun.server.service;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.lixiyun.pojo.dto.chat.ChatDTO;

/**
 * @author lixiyun
 * @since 2026-03-16 13:02
 */
public interface ChatService {

    void chat(ChatDTO chatDTO) throws GraphStateException;
}
