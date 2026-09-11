package org.lixiyun.server.ai.node.diagnosis.input;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.MessageEffectiveLevel;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.RoundEffectiveLevel;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.SessionCleanResult;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 输入侧-数据预清洗节点
 * @author lixiyun
 * @since 2026-08-10 10:41
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreCleanNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "preCleanNode";

    private static final Pattern NOISE_PATTERN = Pattern.compile("^[\\s\\p{P}\\p{S}]+$");

    private static final Set<String> WEAK_SEMANTIC_DICT = Set.of(
            "嗯", "哦", "啊", "哈",
            "嗯嗯", "哦哦", "嗯嗯嗯",
            "好的", "收到", "行", "可以", "知道了", "嗯嗯好的", "哦知道了"
    );

    private static final int WEAK_MAX_LENGTH = 5;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("输入侧-数据预清洗-开始");

        Optional<ConversationProcessContextBO> contextBOOpt = state.value(ConversationProcessContextBO.NAME);
        if (contextBOOpt.isEmpty()) {
            log.error("输入侧-数据预清洗-会话上下文为空");
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_PROCESS_CONTEXT_NOT_EXIST);
        }

        Optional<InputResult> inputResultOpt = state.value(InputResult.NAME);
        if (inputResultOpt.isEmpty()) {
            log.error("输入侧-数据预清洗-输入侧流程聚合结果为空");
            throw new BusinessException(ConversationExceptionEnum.INPUT_RESULT_NOT_EXIST);
        }


        ConversationProcessContextBO contextBO = contextBOOpt.get();
        List<ConversationMemory> conversationHistory = contextBO.getConversationHistory();
        List<ConversationMemory> temporaryMessages = contextBO.getTemporaryMessages();
        List<EmotionAnalysis> emotionAnalyses = contextBO.getEmotionAnalyses();
        Long userId = contextBO.getConversation().getUserId();
        Long conversationId = contextBO.getConversation().getId();

        List<ConversationMemory> allMessages = mergeAndSortMessages(conversationHistory, temporaryMessages);
        log.info("输入侧-数据预清洗-消息时序排序完成，总消息数：{}", allMessages.size());

        Map<Integer, EmotionAnalysis> emotionByRound = emotionAnalyses != null
                ? emotionAnalyses.stream().collect(Collectors.toMap(EmotionAnalysis::getRoundNum, e -> e, (a, b) -> b))
                : Map.of();

        List<RoundEffectiveLevel> roundEffectiveLevelList = buildRoundEffectiveLevels(allMessages, emotionByRound);
        log.info("输入侧-数据预清洗-轮次聚合与有效性校验完成，轮次数：{}", roundEffectiveLevelList.size());

        SessionCleanResult sessionCleanResult = SessionCleanResult.builder()
                .userId(userId)
                .conversationId(conversationId)
                .roundEffectiveLevelList(roundEffectiveLevelList)
                .build();

        inputResultOpt.get().setSessionCleanResult(sessionCleanResult);

        log.info("输入侧-数据预清洗-完成");

        return Map.of();
    }

    /**
     * 按轮次聚合并校验消息有效性
     * 1. 按轮次聚合消息
     * 2. 校验每个消息的有效性
     * 3. 计算轮次级有效级别
     * @param sortedMessages 已按轮次排序的消息列表
     * @param emotionByRound 每个轮次的情绪分析结果映射
     * @return 每个轮次的有效级别消息列表
     * @see RoundEffectiveLevel
     */
    private List<RoundEffectiveLevel> buildRoundEffectiveLevels(List<ConversationMemory> sortedMessages,
                                                                 Map<Integer, EmotionAnalysis> emotionByRound) {
        List<RoundEffectiveLevel> result = new ArrayList<>();
        int currentRound = -1;
        List<MessageEffectiveLevel> currentMessageLevels = null;
        int currentRoundLevel = SessionCleanResult.LEVEL_INVALID;

        for (ConversationMemory msg : sortedMessages) {
            if (msg.getRoundNum() == null) {
                continue;
            }
            int roundNum = msg.getRoundNum();
            if (roundNum != currentRound) {
                if (currentRound != -1) {
                    result.add(flushRound(currentRound, currentRoundLevel, currentMessageLevels, emotionByRound));
                }
                currentRound = roundNum;
                currentMessageLevels = new ArrayList<>();
                currentRoundLevel = SessionCleanResult.LEVEL_INVALID;
            }

            int msgLevel = classifyMessageLevel(msg.getContent());
            currentMessageLevels.add(MessageEffectiveLevel.builder()
                    .level(msgLevel)
                    .conversationMemory(msg)
                    .build()
            );
            currentRoundLevel = Math.min(currentRoundLevel, msgLevel);
        }

        if (currentRound != -1) {
            result.add(flushRound(currentRound, currentRoundLevel, currentMessageLevels, emotionByRound));
        }

        return result;
    }

    /**
     * 轮次级有效级别计算
     * @param roundNum 轮次号
     * @param roundLevel 轮次级有效级别
     * @param messageLevels 该轮次下的所有消息级清洗结果
     * @param emotionByRound 每个轮次的情绪分析结果映射
     * @return 轮次级有效级别消息
     * @see RoundEffectiveLevel
     * 计算规则：取轮内所有消息的最高有效等级（valid > weak > invalid）
     */
    private RoundEffectiveLevel flushRound(int roundNum, int roundLevel,
                                           List<MessageEffectiveLevel> messageLevels,
                                           Map<Integer, EmotionAnalysis> emotionByRound) {
        return RoundEffectiveLevel.builder()
                .roundNum(roundNum)
                .level(roundLevel)
                .emotionAnalysis(emotionByRound.get(roundNum))
                .messageEffectiveLevelList(messageLevels)
                .build();
    }

    /**
     * 合并并排序所有消息
     * @param history 会话历史消息列表
     * @param temporary 临时消息列表
     * @return 合并并排序后的消息列表
     */
    private List<ConversationMemory> mergeAndSortMessages(List<ConversationMemory> history, List<ConversationMemory> temporary) {
        List<ConversationMemory> all = new ArrayList<>();
        if (history != null) {
            all.addAll(history);
        }
        if (temporary != null) {
            all.addAll(temporary);
        }
        all.sort(Comparator
                .comparingInt((ConversationMemory m) -> m.getRoundNum() != null ? m.getRoundNum() : Integer.MAX_VALUE)
                .thenComparing(m -> m.getCreatedTime() != null ? m.getCreatedTime() : LocalDateTime.MIN));
        return all;
    }

    /**
     * 消息级有效级别分类
     * @param content 消息内容
     * @return 消息级有效级别
     * 0=valid有效（可参与情绪分析）
     * 1=weak弱语义（低权重纳入统计）
     * 2=invalid无效（直接过滤）
     */
    private int classifyMessageLevel(String content) {
        if (content == null || content.isBlank()) {
            return SessionCleanResult.LEVEL_INVALID;
        }
        if (NOISE_PATTERN.matcher(content).matches()) {
            return SessionCleanResult.LEVEL_INVALID;
        }
        String stripped = content.replaceAll("[\\s\\p{P}\\p{S}]", "");
        if (!stripped.isEmpty() && stripped.length() <= WEAK_MAX_LENGTH && WEAK_SEMANTIC_DICT.contains(stripped)) {
            return SessionCleanResult.LEVEL_WEAK;
        }
        return SessionCleanResult.LEVEL_VALID;
    }

    @Override
    public Object inputSummary(OverAllState state) {
        return state.value(ConversationProcessContextBO.NAME).orElse(null);
    }

    @Override
    public Object outputSummary(OverAllState state) {
        Optional<InputResult> irOpt = state.value(InputResult.NAME);
        return irOpt.map(ir -> Map.of(
                "sessionCleanResult", (Object) ir.getSessionCleanResult()
        )).orElse(null);
    }
}