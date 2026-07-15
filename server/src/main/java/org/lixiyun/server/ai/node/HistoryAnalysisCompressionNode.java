package org.lixiyun.server.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.HistoryCompressionBO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.server.ai.model.ChatModelFactory;
import org.lixiyun.server.ai.model.HistoryAnalysisCompressionModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author lixiyun
 * @since 2026-07-15 15:29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryAnalysisCompressionNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "historyAnalysisCompressionNode";

    private final HistoryAnalysisCompressionModel historyAnalysisCompressionModel;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        return Map.of();
    }

    public String apply(HistoryCompressionBO historyCompressionBO) {
        Conversation conversation = historyCompressionBO.getConversation();
        List<EmotionAnalysis> emotionAnalyses = historyCompressionBO.getEmotionAnalyses();

        String prompt = buildPrompt(conversation, emotionAnalyses);

        AssistantMessage call;
        try {
            call = historyAnalysisCompressionModel.call(chatModelFactory.getOllamaChatModel(), prompt);
        } catch (GraphRunnerException e) {
            throw new BusinessException(AIChatExceptionEnum.LLM_CALL_FAILED);
        }
        return call.getText();
    }

    private String buildPrompt(Conversation conversation, List<EmotionAnalysis> emotionAnalyses) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("请将以下对话历史情绪分析结果压缩为简洁的摘要：\n\n");

        if (emotionAnalyses == null || emotionAnalyses.isEmpty()) {
            return "";
        }

        if (conversation.getAnalysisContextSummary() != null && !conversation.getAnalysisContextSummary().isEmpty()) {
            promptBuilder.append("\n【历史情绪分析压缩上下文】\n");
            promptBuilder.append(conversation.getAnalysisContextSummary()).append("\n");
        }

        promptBuilder.append("\n【历史情绪分析结果】\n");
        emotionAnalyses.forEach(analysis ->
                promptBuilder.append("- ").append(analysis.toString()).append("\n")
        );

        log.debug("构建历史情绪分析结果压缩提示词完成，分析数量：{}，会话ID：{}", emotionAnalyses.size(), conversation.getId());

        return promptBuilder.toString();
    }
}