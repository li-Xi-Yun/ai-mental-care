package org.lixiyun.server.ai.node.conversation;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.bo.conversation.HistoryCompressionBO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.model.conversation.HistoryMessageCompressionModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

;

/**
 * @author lixiyun
 * @since 2026-07-15 15:27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryMessageCompressionNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "historyMessageCompressionNode";

    private final HistoryMessageCompressionModel historyMessageCompressionModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        return Map.of();
    }

    public String apply(HistoryCompressionBO historyCompressionBO) {
        Conversation conversation = historyCompressionBO.getConversation();
        List<ConversationMemory> historyMessages = historyCompressionBO.getHistoryMessages();

        String prompt = buildPrompt(conversation, historyMessages);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));

        AssistantMessage call;
        try {
            call = historyMessageCompressionModel.call(chatModel, prompt, aiNodeConfig);
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
        return call.getText();
    }

    private String buildPrompt(Conversation conversation, List<ConversationMemory> historyMessages) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("请将以下对话历史压缩为简洁的摘要：\n\n");

        if (historyMessages == null || historyMessages.isEmpty()) {
            return "";
        }

        if (conversation.getContextSummary() != null && !conversation.getContextSummary().isEmpty()) {
            promptBuilder.append("\n【历史消息压缩上下文】\n");
            promptBuilder.append(conversation.getContextSummary()).append("\n");
        }

        for (ConversationMemory message : historyMessages) {
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                String roleLabel = MessageType.getDescription(message.getType());
                promptBuilder.append(roleLabel).append("：").append(message.getContent()).append("\n");
            }
        }

        log.debug("构建历史消息压缩提示词完成，消息数量：{}，会话ID：{}", historyMessages.size(), conversation.getId());

        return promptBuilder.toString();
    }

}