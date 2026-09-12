package org.lixiyun.server.ai.node.conversation;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.model.conversation.ConversationNameGenerationModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 会话名称生成节点
 * <p>根据用户首次对话内容，调用大模型生成简洁的会话名称</p>
 *
 * @author lixiyun
 * @since 2026-08-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationNameGenerationNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "conversationNameGenerationNode";

    private final ConversationNameGenerationModel conversationNameGenerationModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        return Map.of();
    }

    public String apply(ConversationProcessContextBO processContext) {
        List<ConversationMemory> temporaryMessages = processContext.getTemporaryMessages();

        String prompt = buildPrompt(temporaryMessages);
        log.debug("会话名称生成节点-构建提示词完成，提示词：{}", prompt);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));

        AssistantMessage call;
        try {
            Conversation conversation = processContext.getConversation();
            ConversationMetadata metadata = ConversationMetadata.builder()
                    .conversationId(conversation.getId())
                    .userId(conversation.getUserId())
                    .currentRound(conversation.getCurrentRound())
                    .build();
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .addMetadata(ConversationMetadata.NAME, metadata)
                    .build();
            call = conversationNameGenerationModel.call(chatModel, prompt, aiNodeConfig, runnableConfig);
            log.debug("会话名称生成节点-模型返回结果：{}", call.getText());
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
        return call.getText();
    }

    private String buildPrompt(List<ConversationMemory> temporaryMessages) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("请根据以下用户消息内容，生成一个简短的会话名称：\n\n");

        if (temporaryMessages == null || temporaryMessages.isEmpty()) {
            return "新对话";
        }

        for (ConversationMemory message : temporaryMessages) {
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                String roleLabel = MessageType.getDescription(message.getType());
                promptBuilder.append(roleLabel).append("：").append(message.getContent()).append("\n");
            }
        }

        log.debug("构建会话名称生成提示词完成，消息数量：{}", temporaryMessages.size());

        return promptBuilder.toString();
    }
}