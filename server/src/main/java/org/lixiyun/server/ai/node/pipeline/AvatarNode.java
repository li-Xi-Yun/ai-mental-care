package org.lixiyun.server.ai.node.pipeline;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.config.Live2dConfig;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.emotion.EmotionAnalysis;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.pipeline.AvatarModel;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.mapper.Live2dConfigMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 虚拟形象动作/表情节点
 * <p>
 * 从缓存中获取会话历史上下文和情绪分析历史上下文，
 * 结合Live2D模型配置的语义提示词和模型生成的文本构建用户提示词，
 * 发送至大模型生成结构化的动作与表情JSON，
 * 最后使用Live2D配置中的白名单进行校验。
 * </p>
 *
 * @author lixiyun
 * @date 2026/10/14 13:53
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "avatarNode";

    private final AvatarModel avatarModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;
    private final Live2dConfigMapper live2dConfigMapper;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        return Map.of();
    }

    /**
     * 执行虚拟形象动作/表情生成与校验
     *
     * @param modelText           本轮模型输出内容
     * @param processContextBO    会话上下文BO，包含会话ID、用户ID、当前轮次、本轮用户输入内容等
     * @return 校验后的表情/动作指令载荷
     */
    public AvatarModel.AvatarCommandPayload apply(String modelText, ConversationProcessContextBO processContextBO) {
        log.debug("[虚拟形象节点] 开始执行，会话ID：{}，轮次：{}", processContextBO.getConversation().getId(), processContextBO.getConversation().getCurrentRound());
        Long conversationId = processContextBO.getConversation().getId();
        Long userId = processContextBO.getConversation().getUserId();
        Integer currentRound = processContextBO.getConversation().getCurrentRound();
        List<ConversationMemory> currentUserMessages = processContextBO.getTemporaryMessages();
        List<ConversationMemory> conversationHistory = new ArrayList<>(processContextBO.getConversationHistory());
        List<EmotionAnalysis> emotionAnalyses = processContextBO.getEmotionAnalyses();

        List<Long> list = currentUserMessages.stream().map(ConversationMemory::getId).toList();
        conversationHistory.removeIf(item -> list.contains(item.getId()));

        Live2dConfig live2dConfig = loadAllowList();

        String prompt = buildPrompt(conversationHistory, emotionAnalyses, currentUserMessages, modelText,
                live2dConfig.getSemanticPrompt(), live2dConfig.getExpressionList(), live2dConfig.getMotionList());
        log.debug("[虚拟形象节点] 构建提示词完成，会话ID：{}", conversationId);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfigWithLoad(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));

        AvatarModel.AvatarCommandPayload result;
        try {
            ConversationMetadata metadata = ConversationMetadata.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .currentRound(currentRound)
                    .build();
            log.debug("[虚拟形象节点] 构建模型调用配置完成，元数据：{}", metadata);
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .addMetadata(ConversationMetadata.NAME, metadata)
                    .build();
            result = avatarModel.callForResult(chatModel, prompt, aiNodeConfig, runnableConfig);
            log.debug("[虚拟形象节点] 模型调用完成，模型返回结果：{}", result);
        } catch (GraphRunnerException e) {
            throw new BusinessException(AIChatExceptionEnum.LLM_CALL_FAILED);
        }

        result = validateAndFallback(result, live2dConfig);

        log.info("[虚拟形象节点] 执行完成，会话ID：{}，expression：{}，motion：{}",
                conversationId, result.expression(), result.motion());
        return result;
    }

    /**
     * 构建发送至模型的提示词
     * <p>输入信息（按拼接顺序）：
     * <ol>
     *   <li>历史会话上下文（剔除当前轮次后的纯历史）</li>
     *   <li>历史情绪分析数据（仅逐轮明细，不含压缩摘要）</li>
     *   <li>本轮用户输入内容</li>
     *   <li>本轮模型输出内容</li>
     *   <li>Live2D 语义提示词与候选词汇（约束模型只能输出其中的固定词汇，且不要求两类都有）</li>
     * </ol></p>
     *
     * @param historicalContext   历史会话上下文（不含本轮 USER/ASSISTANT）
     * @param emotionAnalyses     历史情绪分析列表
     * @param currentUserMessages 本轮用户输入内容
     * @param modelText           本轮模型输出内容
     * @param semanticPrompt      Live2D 语义提示词（语义规则描述）
     * @param expressionList      Live2D 表情候选固定词汇
     * @param motionList          Live2D 动作候选固定词汇
     * @return 拼接后的提示词
     */
    private String buildPrompt(List<ConversationMemory> historicalContext,
                               List<EmotionAnalysis> emotionAnalyses,
                               List<ConversationMemory> currentUserMessages,
                               String modelText,
                               String semanticPrompt,
                               List<String> expressionList,
                               List<String> motionList) {
        StringBuilder promptBuilder = new StringBuilder();

        // 1. 历史会话上下文：过往对话记录，帮助模型理解用户背景、话题脉络与既往表达方式
        if (historicalContext != null && !historicalContext.isEmpty()) {
            promptBuilder.append("【历史会话上下文】\n");
            promptBuilder.append("说明：以下是该用户在本轮之前的过往对话记录，供你理解用户的表达习惯、情感脉络与对话背景。\n");
            historicalContext.forEach(memory ->
                    promptBuilder.append("- [").append(memory.getType()).append("] ").append(memory.getContent()).append("\n")
            );
            promptBuilder.append("\n");
        }

        // 2. 历史情绪分析上下文：历轮情绪评估结果，辅助判断用户当前情绪倾向
        if (emotionAnalyses != null && !emotionAnalyses.isEmpty()) {
            promptBuilder.append("【历史情绪分析上下文】\n");
            promptBuilder.append("说明：以下是历轮对用户情绪状态的自动评估明细（不含历史压缩摘要），帮助你掌握用户的情绪变化趋势。\n");
            emotionAnalyses.forEach(analysis ->
                    promptBuilder.append("- 第").append(analysis.getRoundNum()).append("轮：")
                            .append(analysis.getEmotionLabel())
                            .append("(").append(analysis.getEmotionSubLabel()).append(")")
                            .append(" 置信度：").append(analysis.getEmotionConfidence())
                            .append(" 强度：").append(analysis.getEmotionIntensity())
                            .append(" 趋势：").append(analysis.getEmotionTrend())
                            .append("\n")
            );
            promptBuilder.append("\n");
        }

        // 3. 本轮用户输入内容：用户当前刚发的消息，是本次交互的触发点与直接依据
        if (currentUserMessages != null && !currentUserMessages.isEmpty()) {
            promptBuilder.append("【本轮用户输入内容】\n");
            promptBuilder.append("说明：以下是用户在本轮发送的消息，请结合它判断此刻用户想表达的情绪与意图。\n");
            currentUserMessages.forEach(memory ->
                    promptBuilder.append("- ").append(memory.getContent()).append("\n")
            );
            promptBuilder.append("\n");
        }

        // 4. 本轮模型输出内容：AI 对用户本轮的回答，动作/表情需与该回复的情绪基调保持一致
        if (modelText != null && !modelText.isBlank()) {
            promptBuilder.append("【本轮模型输出内容】\n");
            promptBuilder.append("说明：以下是 AI 助手对本轮用户的回复正文，你选择的表情与动作应与这段回复的情感基调相匹配。\n");
            promptBuilder.append(modelText).append("\n\n");
        }

        // 5. Live2D 语义提示词与候选词汇：约束模型必须从固定词汇中输出，且两字段不要求同时存在
        if ((semanticPrompt != null && !semanticPrompt.isBlank())
                || (expressionList != null && !expressionList.isEmpty())
                || (motionList != null && !motionList.isEmpty())) {
            promptBuilder.append("【Live2D 语义提示词与候选词汇】\n");
            promptBuilder.append("说明：该虚拟形象模型的动作（motion）与表情（expression）只能从下方候选固定词汇中选取，"
                    + "不得使用候选词汇以外的任何内容；两类不要求同时出现，可以只输出其中一类。\n");
            if (semanticPrompt != null && !semanticPrompt.isBlank()) {
                promptBuilder.append("语义规则：").append(semanticPrompt).append("\n");
            }
            if (expressionList != null && !expressionList.isEmpty()) {
                promptBuilder.append("可选表情固定词汇（expression）：")
                        .append(String.join("、", expressionList)).append("\n");
            }
            if (motionList != null && !motionList.isEmpty()) {
                promptBuilder.append("可选动作固定词汇（motion）：")
                        .append(String.join("、", motionList)).append("\n");
            }
            promptBuilder.append("输出约束：expression 与 motion 必须严格从上方对应候选词汇中选择；"
                    + "若无合适匹配项则对应字段输出 null；二者可只输出其一，也可都为空。");
        }

        log.debug("[虚拟形象节点] 构建提示词完成");
        return promptBuilder.toString();
    }

    @Override
    public Object inputSummary(OverAllState state) {
        return null;
    }

    @Override
    public Object outputSummary(OverAllState state) {
        return null;
    }

    /**
     * 校验模型输出的动作/表情是否在白名单内，不在则降级为null
     */
    private AvatarModel.AvatarCommandPayload validateAndFallback(
            AvatarModel.AvatarCommandPayload payload, Live2dConfig config) {
        List<String> expressionList = config.getExpressionList();
        List<String> motionList = config.getMotionList();

        // 校验降级：白名单为空（模型无对应资产）时强制置 null，而非跳过校验——防止模型幻觉输出非白名单资产透传下发
        String validExpression = null;
        if (payload.expression() != null && expressionList != null && !expressionList.isEmpty()
                && expressionList.contains(payload.expression())) {
            validExpression = payload.expression();
        } else if (payload.expression() != null) {
            log.warn("[虚拟形象节点] 表情 {} 不在白名单 {} 中，降级为空", payload.expression(), expressionList);
        }

        String validMotion = null;
        if (payload.motion() != null && motionList != null && !motionList.isEmpty()
                && motionList.contains(payload.motion())) {
            validMotion = payload.motion();
        } else if (payload.motion() != null) {
            log.warn("[虚拟形象节点] 动作 {} 不在白名单 {} 中，降级为空", payload.motion(), motionList);
        }

        return new AvatarModel.AvatarCommandPayload(validExpression, validMotion);
    }

    /**
     * 加载白名单：查询当前启用的 Live2D 模型配置，提取 expressionList / motionList。
     * 查询异常或未配置模型时抛出异常。
     */
    private Live2dConfig loadAllowList() {
        Live2dConfig config = live2dConfigMapper.selectOne(new LambdaQueryWrapper<Live2dConfig>()
                .eq(Live2dConfig::getEnabled, Live2dConfig.ENABLED_YES)
                .orderByAsc(Live2dConfig::getSort)
                .last("limit 1"));
        if (config == null) {
            log.error("[虚拟形象节点] 未配置启用的 Live2D 模型");
            throw new BusinessException(AIChatExceptionEnum.LLM_CALL_FAILED);
        }
        return config;
    }
}