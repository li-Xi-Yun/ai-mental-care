package org.lixiyun.server.ai.node.conversation;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.server.ai.model.conversation.EmotionRecognitionModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.mapper.EmotionAnalysisMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;
    private final EmotionAnalysisMapper emotionAnalysisMapper;
    private final ConversationCacheManager conversationCacheManager;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws GraphRunnerException {
        log.debug("情感识别节点-开始执行");
        return null;
    }

    public EmotionRecognitionModel.EmotionRecognitionResult apply(ConversationProcessContextBO processContext) {
        log.debug("情感识别节点-开始执行");

        Conversation conversation = processContext.getConversation();
        List<ConversationMemory> conversationHistory = processContext.getConversationHistory();
        List<ConversationMemory> temporaryMessages = processContext.getTemporaryMessages();
        List<EmotionAnalysis> emotionAnalyses = processContext.getEmotionAnalyses();

        String prompt = buildPrompt(conversation, conversationHistory, temporaryMessages, emotionAnalyses);
        log.debug("情感识别节点-构建提示词完成，会话ID：{}，提示词：{}", conversation.getId(), prompt);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));

        EmotionRecognitionModel.EmotionRecognitionResult result;
        try {
            result = emotionRecognitionModel.callForResult(chatModel, prompt, aiNodeConfig);
            log.debug("情感识别节点-模型返回结果：{}", result);
        } catch (GraphRunnerException e) {
            throw new BusinessException(AIChatExceptionEnum.LLM_CALL_FAILED);
        }

        EmotionAnalysis emotionAnalysis = BeanUtil.copyProperties(result, EmotionAnalysis.class);
        emotionAnalysis.setConversationId(conversation.getId());
        emotionAnalysis.setUserId(conversation.getUserId());
        emotionAnalysis.setRoundNum(conversation.getCurrentRound());

        emotionAnalysisMapper.insert(emotionAnalysis);
        log.debug("情感识别节点-持久化数据：{}", emotionAnalysis);
        log.info("情感识别节点-情绪分析结果已持久化，会话ID：{}，轮次：{}", conversation.getId(), conversation.getCurrentRound());

        try {
            List<EmotionAnalysis> cachedEmotionAnalyses = (List<EmotionAnalysis>) conversationCacheManager.getCacheMapValue(
                    conversation.getId(), ConversationCacheConstant.HASH_FIELD_EMOTION_ANALYSIS_LIST);
            if (cachedEmotionAnalyses == null) {
                cachedEmotionAnalyses = new ArrayList<>();
            }
            cachedEmotionAnalyses.add(emotionAnalysis);
            conversationCacheManager.updateCacheMapValue(
                    conversation.getId(), ConversationCacheConstant.HASH_FIELD_EMOTION_ANALYSIS_LIST, cachedEmotionAnalyses);
            log.info("情感识别节点-情绪分析结果已追加至Redis缓存，会话ID：{}，轮次：{}，当前缓存条数：{}",
                    conversation.getId(), conversation.getCurrentRound(), cachedEmotionAnalyses.size());
        } catch (Exception e) {
            log.error("情感识别节点-追加Redis缓存失败（不影响主流程），会话ID：{}，错误：{}", conversation.getId(), e.getMessage(), e);
        }

        return result;
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