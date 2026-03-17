package org.lixiyun.server.infrastructure.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.Data;
import org.lixiyun.server.config.prompt.PromptWord;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-01-20 22:40
 */
@Data
@Repository
public class CommonServerAgent {

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel chatModel;

    public static final String NAME = "CommonServerAgent";
    public String semanticCompression(List<Message> messages) {
        ReactAgent semanticCompressionAgent = ReactAgent.builder()
                .model(chatModel)
                .name("semantic-compression")
                .description("语义压缩")
                .systemPrompt(PromptWord.SEMANTIC_COMPRESSION_SYSTEM_PROMPT)
                .chatOptions(ChatOptions.builder()
                        .topK(40)
                        .topP(0.9)
                        .frequencyPenalty(0.5)
                        .presencePenalty(0.5)
                        .temperature(0.1)
                        .maxTokens(1024)
                        .build()
                )
                .enableLogging(false)
                .build();
        try {
            AssistantMessage call = semanticCompressionAgent.call(messages);
            return call.getText();
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }

}
