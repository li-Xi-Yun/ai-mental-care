package org.lixiyun.server.ai.node.diagnosis.process;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisData;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisDataRequest;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.CoreInfoExtractResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.KeyEventItem;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.model.diagnosis.process.DiseaseCourseAttributionProcessModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 诊断处理侧-病程归因组节点
 * 从对话中提取核心触发场景、触发关键词、症状持续时长、发作模式等病程归因信息
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiseaseCourseAttributionNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "diseaseCourseAttributionNode";

    private final DiseaseCourseAttributionProcessModel diseaseCourseAttributionProcessModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断处理侧-病程归因组-开始");

        Optional<DiagnosisDataRequest> diagnosisDataRequestOpt = state.value(DiagnosisDataRequest.NAME);
        if (diagnosisDataRequestOpt.isEmpty()) {
            log.error("诊断处理侧-病程归因组-诊断数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_NOT_EXIST);
        }
        DiagnosisDataRequest diagnosisDataRequest = diagnosisDataRequestOpt.get();
        InputResult inputResult = diagnosisDataRequest.getInputResult();
        KnowledgeRetrieveResult knowledgeRetrieveResult = diagnosisDataRequest.getKnowledgeRetrieveResult();

        String userPrompt = buildUserPrompt(inputResult, knowledgeRetrieveResult);
        log.debug("诊断处理侧-病程归因组-构建用户提示词完成，提示词：{}", userPrompt);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
        DiseaseCourseAttributionProcessModel.DiseaseCourseAttributionResult result = diseaseCourseAttributionProcessModel.callForResult(chatModel, userPrompt, aiNodeConfig);
        log.debug("诊断处理侧-病程归因组-模型返回结果：{}", result);

        if (result == null) {
            log.error("诊断处理侧-病程归因组-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_PROCESS_RESULT_PARSE_FAILED);
        }

        Optional<DiagnosisData> diagnosisDataOpt = state.value(DiagnosisData.NAME);
        if (diagnosisDataOpt.isEmpty()) {
            log.error("诊断处理侧-病程归因组-诊断结果数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_RESULT_NOT_EXIST);
        }
        DiagnosisData diagnosisData = diagnosisDataOpt.get();
        diagnosisData.setCoreTriggerScene(result.getCoreTriggerScene());
        diagnosisData.setCoreTriggerKeywords(result.getCoreTriggerKeywords());
        diagnosisData.setTriggerRoundNum(result.getTriggerRoundNum());
        diagnosisData.setSymptomDuration(result.getSymptomDuration());
        diagnosisData.setOnsetPattern(result.getOnsetPattern());
        diagnosisData.setFirstTriggerDesc(result.getFirstTriggerDesc());
        log.debug("诊断处理侧-病程归因组-写入诊断数据完成，结果：{}", result);

        log.info("诊断处理侧-病程归因组-完成，核心触发场景：{}，发作模式：{}",
                result.getCoreTriggerScene(), result.getOnsetPattern());
        return Map.of();
    }

    private String buildUserPrompt(InputResult inputResult, KnowledgeRetrieveResult knowledgeRetrieveResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下用户信息，分析病程归因相关内容：\n\n");

        CoreInfoExtractResult coreInfo = inputResult.getCoreInfoExtractResult();
        if (coreInfo != null) {
            if (coreInfo.getCoreAppeal() != null && !coreInfo.getCoreAppeal().isBlank()) {
                sb.append("## 核心诉求\n").append(coreInfo.getCoreAppeal()).append("\n\n");
            }

            List<KeyEventItem> keyEvents = coreInfo.getKeyEventTimeline();
            if (keyEvents != null && !keyEvents.isEmpty()) {
                sb.append("## 关键事件时间线\n");
                for (KeyEventItem event : keyEvents) {
                    sb.append("- 第").append(event.getRoundNum()).append("轮：").append(event.getEventDesc()).append("\n");
                }
                sb.append("\n");
            }

            if (coreInfo.getBackgroundSummary() != null && !coreInfo.getBackgroundSummary().isBlank()) {
                sb.append("## 背景信息\n").append(coreInfo.getBackgroundSummary()).append("\n\n");
            }
        }

        if (inputResult.getSymptomNormalizeResult() != null
                && inputResult.getSymptomNormalizeResult().getTermList() != null
                && !inputResult.getSymptomNormalizeResult().getTermList().isEmpty()) {
            sb.append("## 标准症状列表\n");
            inputResult.getSymptomNormalizeResult().getTermList().forEach(term -> {
                if (term.getSymptomDict() != null) {
                    sb.append("- ").append(term.getSymptomDict().getSymptomTerm());
                    if (term.getAppearCount() != null) {
                        sb.append("（出现").append(term.getAppearCount()).append("次");
                        if (term.getRoundList() != null && !term.getRoundList().isEmpty()) {
                            sb.append("，轮次：").append(term.getRoundList());
                        }
                        sb.append("）");
                    }
                    sb.append("\n");
                }
            });
            sb.append("\n");
        }

        if (inputResult.getEmotionStatisticsResult() != null
                && inputResult.getEmotionStatisticsResult().getBaseInfo() != null
                && inputResult.getEmotionStatisticsResult().getBaseInfo().getDominantEmotion() != null) {
            sb.append("## 主导情绪\n")
                    .append(inputResult.getEmotionStatisticsResult().getBaseInfo().getDominantEmotion().getLabel())
                    .append("\n\n");
        }

        if (knowledgeRetrieveResult != null && knowledgeRetrieveResult.getSymptomReferencePrompt() != null
                && !knowledgeRetrieveResult.getSymptomReferencePrompt().isBlank()) {
            sb.append("## 症状知识参考\n").append(knowledgeRetrieveResult.getSymptomReferencePrompt()).append("\n\n");
        }

        if (inputResult.getHistoryDiagnosisSummaryResult() != null
                && inputResult.getHistoryDiagnosisSummaryResult().getFirstDiagnosisPrompt() != null
                && !inputResult.getHistoryDiagnosisSummaryResult().getFirstDiagnosisPrompt().isBlank()) {
            sb.append("## 历史诊断摘要\n")
                    .append(inputResult.getHistoryDiagnosisSummaryResult().getFirstDiagnosisPrompt()).append("\n\n");
        }

        sb.append("请分析并输出病程归因结果，包括核心触发场景、触发关键词、首次出现轮次、症状持续时长、发作模式和首次触发事件描述。\n");
        return sb.toString();
    }

    @Override
    public Object inputSummary(OverAllState state) {
        return state.value(DiagnosisDataRequest.NAME).orElse(null);
    }

    @Override
    public Object outputSummary(OverAllState state) {
        Optional<DiagnosisData> dataOpt = state.value(DiagnosisData.NAME);
        return dataOpt.map(data -> Map.of(
                "coreTriggerScene", (Object) data.getCoreTriggerScene(),
                "coreTriggerKeywords", (Object) data.getCoreTriggerKeywords(),
                "triggerRoundNum", (Object) data.getTriggerRoundNum(),
                "symptomDuration", (Object) data.getSymptomDuration(),
                "onsetPattern", (Object) data.getOnsetPattern(),
                "firstTriggerDesc", (Object) data.getFirstTriggerDesc()
        )).orElse(null);
    }
}