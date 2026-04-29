package org.lixiyun.server.ai.infrastructure.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.Builder;
import lombok.Data;
import org.lixiyun.common.agent.constant.prompt.CommonConstant;
import org.lixiyun.common.agent.prompt.utils.PromptUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-01-20 22:40
 */
@Data
@Builder
public class CommonServerAgent {

    private ChatModel chatModel;

    /**
     * 语义压缩
     * @param messages 会话上下文信息
     * @return 压缩后的语义文本
     */
    public String semanticCompression(List<Message> messages) {
        String prompt = PromptUtil.getPrompt(CommonConstant.SEMANTIC_COMPRESSION_SYSTEM_PROMPT);
        ReactAgent semanticCompressionAgent = ReactAgent.builder()
                .model(chatModel)
                .name("semantic-compression")
                .description("语义压缩")
                .systemPrompt(prompt)
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
            throw new BusinessException(ConversationExceptionEnum.SEMANTIC_COMPRESSION_ERROR);
        }
    }

    /**
     * 会话名称提取
     * @param messages 第一次对话时的用户输入与模型输出
     * @return 会话名称
     */
    public String conversationNameExtraction(List<String> messages){
        String prompt = PromptUtil.getPrompt(CommonConstant.CONVERSATION_NAME_EXTRACTION_SYSTEM_PROMPT);
        ReactAgent conversationNameExtractionAgent = ReactAgent.builder()
                .model(chatModel)
                .name("conversation-name-extraction")
                .description("会话名称提取")
                .systemPrompt(prompt)
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
            AssistantMessage call = conversationNameExtractionAgent.call(messages.toString());
            return call.getText();
        } catch (GraphRunnerException e) {
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NAME_EXTRACTION_ERROR);
        }
    }

}
