package org.lixiyun.server.ai.node.tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.bo.conversation.tool.ScaleToolResult;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.tool.ScaleAnalysisModel;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 量表分析节点
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScaleAnalysisNode {

    public static final String NODE_NAME = "scaleAnalysisNode";

    private final ScaleAnalysisModel scaleAnalysisModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    public ScaleToolResult apply(Long conversationId, Long userId, Long scaleRecordId)  throws BusinessException {
        log.debug("[量表分析节点] 开始执行，会话ID：{}，量表记录ID：{}", conversationId, scaleRecordId);


//        String prompt = buildPrompt(scaleName, questionsAndAnswers, userContext);
        String prompt = "";
        log.debug("[量表分析节点] 构建提示词完成，会话ID：{}", conversationId);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfigWithLoad(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));

        ScaleToolResult result;
        try {
            ConversationMetadata metadata = ConversationMetadata.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .build();
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .addMetadata(ConversationMetadata.NAME, metadata)
                    .build();
            result = scaleAnalysisModel.callForResult(chatModel, prompt, aiNodeConfig, runnableConfig);
            log.debug("[量表分析节点] 模型调用完成，会话ID：{}，分析文本长度：{}",
                    conversationId, result.getAnalysisText() != null ? result.getAnalysisText().length() : 0);
        } catch (GraphRunnerException e) {
            throw new BusinessException(AIChatExceptionEnum.LLM_CALL_FAILED);
        }

        log.info("[量表分析节点] 量表分析完成，会话ID：{}，量表记录ID：{}", conversationId, scaleRecordId);
        return result;
    }

    private String buildPrompt(String scaleName, String questionsAndAnswers, String userContext) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("【量表名称】\n").append(scaleName).append("\n\n");

        promptBuilder.append("【用户答题内容】\n").append(questionsAndAnswers).append("\n\n");

        if (userContext != null && !userContext.isEmpty()) {
            promptBuilder.append("【用户近期情绪状态与对话上下文】\n").append(userContext).append("\n");
        }

        log.debug("[量表分析节点] 提示词构建完成，量表：{}", scaleName);
        return promptBuilder.toString();
    }
}