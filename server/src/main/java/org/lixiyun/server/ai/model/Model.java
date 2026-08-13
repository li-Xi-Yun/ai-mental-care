package org.lixiyun.server.ai.model;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

/**
 * @author lixiyun
 * @since 2026-08-13 15:59
 */
public interface Model {

    AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException;

    Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt) throws GraphRunnerException;
}