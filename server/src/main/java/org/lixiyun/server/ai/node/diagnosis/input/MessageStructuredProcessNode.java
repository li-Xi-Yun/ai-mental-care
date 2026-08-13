package org.lixiyun.server.ai.node.diagnosis.input;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.MessageEffectiveLevel;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.RoundEffectiveLevel;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.SessionCleanResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.CoreInfoExtractResult;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.model.ChatModelFactory;
import org.lixiyun.server.ai.model.diagnosis.input.MessageStructuredProcessModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 输入侧-消息结构化处理节点
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>步骤1：从上一阶段输出的结构化轮次单元集合中筛选有效消息（level=valid）</li>
 *   <li>步骤2：空数据校验，有效消息为空则终止流程</li>
 *   <li>步骤3：有效轮次阈值分支判断
 *     <ul>
 *       <li>分支A（未达阈值）：规整为标准对话格式，返回原文</li>
 *       <li>分支B（达到阈值）：调用轻量化大模型提取核心信息，返回结构化核心信息清单</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * @author lixiyun
 * @since 2026-08-10 14:47
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageStructuredProcessNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "messageStructuredProcessNode";

    /** 有效轮次阈值 */
    private static final int ROUND_THRESHOLD = 3;

    private final MessageStructuredProcessModel messageStructuredProcessModel;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("输入侧-消息结构化处理-开始");

        Optional<InputResult> inputResultOpt = state.value(InputResult.NAME);
        if (inputResultOpt.isEmpty()) {
            log.error("输入侧-消息结构化处理-输入侧流程聚合结果为空");
            throw new BusinessException(ConversationExceptionEnum.INPUT_RESULT_NOT_EXIST);
        }
        InputResult inputResult = inputResultOpt.get();
        SessionCleanResult sessionCleanResult = inputResult.getSessionCleanResult();
        if (sessionCleanResult == null || sessionCleanResult.getRoundEffectiveLevelList() == null) {
            log.error("输入侧-消息结构化处理-清洗结果为空");
            throw new BusinessException(ConversationExceptionEnum.SESSION_CLEAN_RESULT_NOT_EXIST);
        }

        List<RoundEffectiveLevel> allRounds = sessionCleanResult.getRoundEffectiveLevelList();

        List<RoundEffectiveLevel> validRounds = allRounds.stream()
                .filter(r -> r.getLevel() != null && r.getLevel() == SessionCleanResult.LEVEL_VALID)
                .collect(Collectors.toList());
        log.info("输入侧-消息结构化处理-有效轮次筛选完成，有效轮次数：{}", validRounds.size());

        if (validRounds.isEmpty()) {
            log.info("输入侧-消息结构化处理-有效消息为空，终止处理流程");
            // todo 输入侧-消息结构化处理-有效消息为空，终止处理流程
            return Map.of();
        }

        int validRoundCount = validRounds.size();
        if (validRoundCount < ROUND_THRESHOLD) {
            log.info("输入侧-消息结构化处理-分支A-有效轮次({})未达到阈值({})，返回对话原文", validRoundCount, ROUND_THRESHOLD);
            String formattedConversation = formatOriginalConversation(validRounds);
            CoreInfoExtractResult coreInfoExtractResult = CoreInfoExtractResult.builder()
                    .coreAppeal(formattedConversation)
                    .keyEventTimeline(List.of())
                    .symptomOriginalList(List.of())
                    .backgroundSummary(null)
                    .build();
            inputResult.setCoreInfoExtractResult(coreInfoExtractResult);
        } else {
            log.info("输入侧-消息结构化处理-分支B-有效轮次({})达到阈值({})，调用大模型提取核心信息", validRoundCount, ROUND_THRESHOLD);
            String userPrompt = buildUserPrompt(validRounds);
            ChatModel chatModel = chatModelFactory.getOllamaChatModel();
            CoreInfoExtractResult coreInfoExtractResult = messageStructuredProcessModel.callForResult(chatModel, userPrompt);
            inputResult.setCoreInfoExtractResult(coreInfoExtractResult);
        }

        log.info("输入侧-消息结构化处理-完成");
        return Map.of();
    }

    private String formatOriginalConversation(List<RoundEffectiveLevel> validRounds) {
        StringBuilder sb = new StringBuilder();
        for (RoundEffectiveLevel round : validRounds) {
            sb.append("【第").append(round.getRoundNum()).append("轮】\n");
            if (round.getMessageEffectiveLevelList() != null) {
                for (MessageEffectiveLevel msgLevel : round.getMessageEffectiveLevelList()) {
                    ConversationMemory memory = msgLevel.getConversationMemory();
                    if (memory != null && memory.getContent() != null) {
                        sb.append("[").append(memory.getType()).append("] ").append(memory.getContent()).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private String buildUserPrompt(List<RoundEffectiveLevel> validRounds) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是用户与AI的心理咨询对话记录，请从中提取核心信息：\n\n");
        for (RoundEffectiveLevel round : validRounds) {
            sb.append("【第").append(round.getRoundNum()).append("轮】\n");
            if (round.getMessageEffectiveLevelList() != null) {
                for (MessageEffectiveLevel msgLevel : round.getMessageEffectiveLevelList()) {
                    if (msgLevel.getLevel() != null && msgLevel.getLevel() == SessionCleanResult.LEVEL_VALID) {
                        ConversationMemory memory = msgLevel.getConversationMemory();
                        if (memory != null && memory.getContent() != null) {
                            sb.append("[").append(memory.getType()).append("] ").append(memory.getContent()).append("\n");
                        }
                    }
                }
            }
        }
        return sb.toString();
    }
}