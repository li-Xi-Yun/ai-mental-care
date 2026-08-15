package org.lixiyun.server.ai.node.conversation;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.server.ai.model.conversation.EmotionRecognitionModel;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.factory.InjectChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 用户情绪分析节点
 * @author lixiyun
 * @since 2026-03-15 13:35
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionRecognitionNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "emotionRecognitionNode";

    private final EmotionRecognitionModel emotionRecognitionModel;

    @InjectChatModel(ChatModelType.DEEP_SEEK)
    private ChatModel chatModel;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws GraphRunnerException {
        log.debug("情感识别节点-开始执行");
        return null;
    }

    public String apply(ConversationProcessContextBO processContext) {
        log.debug("情感识别节点-开始执行");

        Conversation conversation = processContext.getConversation();
        List<ConversationMemory> conversationHistory = processContext.getConversationHistory();
        List<ConversationMemory> temporaryMessages = processContext.getTemporaryMessages();
        List<EmotionAnalysis> emotionAnalyses = processContext.getEmotionAnalyses();

        String prompt = buildPrompt(conversation, conversationHistory, temporaryMessages, emotionAnalyses);

        AssistantMessage call;
        try {
            call = emotionRecognitionModel.call(chatModel, prompt);
        } catch (GraphRunnerException e) {
            throw new BusinessException(AIChatExceptionEnum.LLM_CALL_FAILED);
        }
        return call.getText();
    }

    private String buildPrompt(Conversation conversation,
                               List<ConversationMemory> conversationHistory,
                               List<ConversationMemory> temporaryMessages,
                               List<EmotionAnalysis> emotionAnalyses) {
        StringBuilder promptBuilder = new StringBuilder();

        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("【历史上下文信息】\n");
            conversationHistory.forEach(memory ->
                    promptBuilder.append("- [").append(memory.getType()).append("] ").append(memory.getContent()).append("\n")
            );
            promptBuilder.append("\n");
        }

        if (temporaryMessages != null && !temporaryMessages.isEmpty()) {
            promptBuilder.append("【本轮用户提问信息】\n");
            temporaryMessages.forEach(memory ->
                    promptBuilder.append("- [").append(memory.getType()).append("] ").append(memory.getContent()).append("\n")
            );
            promptBuilder.append("\n");
        }

        if (emotionAnalyses != null && !emotionAnalyses.isEmpty()) {
            promptBuilder.append("【历史分析数据信息】\n");
            if (conversation.getAnalysisContextSummary() != null && !conversation.getAnalysisContextSummary().isEmpty()) {
                promptBuilder.append("历史情绪分析压缩摘要：").append(conversation.getAnalysisContextSummary()).append("\n\n");
            }
            emotionAnalyses.forEach(analysis ->
                    promptBuilder.append("- 第").append(analysis.getRoundNum()).append("轮：")
                            .append(analysis.getEmotionLabel())
                            .append("(").append(analysis.getEmotionSubLabel()).append(")")
                            .append(" 置信度：").append(analysis.getEmotionConfidence())
                            .append(" 强度：").append(analysis.getEmotionIntensity())
                            .append(" 趋势：").append(analysis.getEmotionTrend())
                            .append("\n")
            );
        }

        log.debug("构建情感识别提示词完成，会话ID：{}", conversation.getId());

        return promptBuilder.toString();
    }

}