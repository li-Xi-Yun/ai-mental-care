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
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.server.ai.model.diagnosis.process.PsychologicalStateProcessModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 诊断处理侧-心理状态与症状评估节点
 * 评估用户整体心理状态、总结核心症状、生成症状标签
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PsychologicalStateNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "psychologicalStateNode";

    private final PsychologicalStateProcessModel psychologicalStateProcessModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断处理侧-心理状态与症状评估-开始");

        Optional<DiagnosisDataRequest> diagnosisDataRequestOpt = state.value(DiagnosisDataRequest.NAME);
        if (diagnosisDataRequestOpt.isEmpty()) {
            log.error("诊断处理侧-心理状态与症状评估-诊断数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_NOT_EXIST);
        }
        DiagnosisDataRequest diagnosisDataRequest = diagnosisDataRequestOpt.get();
        InputResult inputResult = diagnosisDataRequest.getInputResult();
        KnowledgeRetrieveResult knowledgeRetrieveResult = diagnosisDataRequest.getKnowledgeRetrieveResult();

        String userPrompt = buildUserPrompt(inputResult, knowledgeRetrieveResult);
        log.info("诊断处理侧-心理状态与症状评估-构建用户提示词完成");

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
        PsychologicalStateProcessModel.PsychologicalStateResult result = psychologicalStateProcessModel.callForResult(chatModel, userPrompt, aiNodeConfig);

        if (result == null) {
            log.error("诊断处理侧-心理状态与症状评估-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_PROCESS_RESULT_PARSE_FAILED);
        }

        Optional<DiagnosisData> diagnosisDataOpt = state.value(DiagnosisData.NAME);
        if (diagnosisDataOpt.isEmpty()) {
            log.error("诊断处理侧-心理状态与症状评估-诊断结果数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_RESULT_NOT_EXIST);
        }
        DiagnosisData diagnosisData = diagnosisDataOpt.get();
        diagnosisData.setPsychologicalState(result.getPsychologicalState());
        diagnosisData.setSymptomSummary(result.getSymptomSummary());
        diagnosisData.setSymptomTags(result.getSymptomTags());

        log.info("诊断处理侧-心理状态与症状评估-完成，心理状态：{}，症状标签：{}",
                result.getPsychologicalState(), result.getSymptomTags());
        return Map.of();
    }

    private String buildUserPrompt(InputResult inputResult, KnowledgeRetrieveResult knowledgeRetrieveResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下用户信息，评估心理状态与症状：\n\n");

        CoreInfoExtractResult coreInfo = inputResult.getCoreInfoExtractResult();
        if (coreInfo != null) {
            if (coreInfo.getCoreAppeal() != null && !coreInfo.getCoreAppeal().isBlank()) {
                sb.append("## 核心诉求\n").append(coreInfo.getCoreAppeal()).append("\n\n");
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
                    if (term.getSymptomDict().getSeverityDefault() != null) {
                        sb.append("（严重程度：").append(term.getSymptomDict().getSeverityDefault()).append("）");
                    }
                    if (term.getAppearCount() != null) {
                        sb.append("，出现").append(term.getAppearCount()).append("次");
                    }
                    sb.append("\n");
                }
            });
            sb.append("\n");
        }

        if (inputResult.getEmotionStatisticsResult() != null
                && inputResult.getEmotionStatisticsResult().getBaseInfo() != null) {
            sb.append("## 情绪统计\n");
            if (inputResult.getEmotionStatisticsResult().getBaseInfo().getDominantEmotion() != null) {
                sb.append("- 主导情绪：").append(inputResult.getEmotionStatisticsResult().getBaseInfo().getDominantEmotion().getLabel()).append("\n");
            }
            if (inputResult.getEmotionStatisticsResult().getBaseInfo().getEmotionDistribution() != null
                    && !inputResult.getEmotionStatisticsResult().getBaseInfo().getEmotionDistribution().isEmpty()) {
                sb.append("- 情绪分布：");
                inputResult.getEmotionStatisticsResult().getBaseInfo().getEmotionDistribution().forEach(item ->
                        sb.append(item.getLabel()).append("(").append(item.getRatio()).append(") "));
                sb.append("\n");
            }
            if (inputResult.getEmotionStatisticsResult().getTrend() != null) {
                sb.append("- 情绪趋势：").append(inputResult.getEmotionStatisticsResult().getTrend().getEmotionTrend()).append("\n");
            }
            sb.append("\n");
        }

        if (knowledgeRetrieveResult != null) {
            if (knowledgeRetrieveResult.getSymptomReferencePrompt() != null && !knowledgeRetrieveResult.getSymptomReferencePrompt().isBlank()) {
                sb.append("## 症状知识参考\n").append(knowledgeRetrieveResult.getSymptomReferencePrompt()).append("\n\n");
            }
            if (knowledgeRetrieveResult.getDiagnosisReferencePrompt() != null && !knowledgeRetrieveResult.getDiagnosisReferencePrompt().isBlank()) {
                sb.append("## 诊断标准参考\n").append(knowledgeRetrieveResult.getDiagnosisReferencePrompt()).append("\n\n");
            }
        }

        if (inputResult.getHistoryDiagnosisSummaryResult() != null
                && inputResult.getHistoryDiagnosisSummaryResult().getFirstDiagnosisPrompt() != null
                && !inputResult.getHistoryDiagnosisSummaryResult().getFirstDiagnosisPrompt().isBlank()) {
            sb.append("## 历史诊断摘要\n")
                    .append(inputResult.getHistoryDiagnosisSummaryResult().getFirstDiagnosisPrompt()).append("\n\n");
        }

        sb.append("请评估并输出心理状态与症状评估结果，包括整体心理状态评估、核心症状总结和症状标签集合。\n");
        return sb.toString();
    }
}