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
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.HistoryDiagnosisSummaryResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.server.ai.model.diagnosis.process.RiskAssessmentProcessModel;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.factory.InjectChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 诊断处理侧-风险评估节点
 * 评估情绪风险等级、自伤/自杀风险等级，判断是否需要人工干预及是否触发危机预警
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAssessmentNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "riskAssessmentNode";

    private final RiskAssessmentProcessModel riskAssessmentProcessModel;
    @InjectChatModel(ChatModelType.DEEP_SEEK)
    private ChatModel chatModel;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断处理侧-风险评估-开始");

        Optional<DiagnosisDataRequest> diagnosisDataRequestOpt = state.value(DiagnosisDataRequest.NAME);
        if (diagnosisDataRequestOpt.isEmpty()) {
            log.error("诊断处理侧-风险评估-诊断数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_NOT_EXIST);
        }
        DiagnosisDataRequest diagnosisDataRequest = diagnosisDataRequestOpt.get();
        InputResult inputResult = diagnosisDataRequest.getInputResult();
        KnowledgeRetrieveResult knowledgeRetrieveResult = diagnosisDataRequest.getKnowledgeRetrieveResult();

        String userPrompt = buildUserPrompt(inputResult, knowledgeRetrieveResult);
        log.info("诊断处理侧-风险评估-构建用户提示词完成");

        RiskAssessmentProcessModel.RiskAssessmentResult result = riskAssessmentProcessModel.callForResult(chatModel, userPrompt);

        if (result == null) {
            log.error("诊断处理侧-风险评估-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_PROCESS_RESULT_PARSE_FAILED);
        }

        Optional<DiagnosisData> diagnosisDataOpt = state.value(DiagnosisData.NAME);
        if (diagnosisDataOpt.isEmpty()) {
            log.error("诊断处理侧-风险评估-诊断结果数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_RESULT_NOT_EXIST);
        }
        DiagnosisData diagnosisData = diagnosisDataOpt.get();
        diagnosisData.setEmotionRiskLevel(result.getEmotionRiskLevel());
        diagnosisData.setEmotionAdjustSuggestion(result.getEmotionAdjustSuggestion());
        diagnosisData.setNeedManualIntervene(result.getNeedManualIntervene());
        diagnosisData.setSelfHarmRiskLevel(result.getSelfHarmRiskLevel());
        diagnosisData.setSuicideRiskLevel(result.getSuicideRiskLevel());
        diagnosisData.setRiskDetail(result.getRiskDetail());
        diagnosisData.setCrisisWarning(result.getCrisisWarning());

        log.info("诊断处理侧-风险评估-完成，情绪风险：{}，自伤风险：{}，自杀风险：{}，人工干预：{}，危机预警：{}",
                result.getEmotionRiskLevel(), result.getSelfHarmRiskLevel(), result.getSuicideRiskLevel(),
                result.getNeedManualIntervene(), result.getCrisisWarning());
        return Map.of();
    }

    private String buildUserPrompt(InputResult inputResult, KnowledgeRetrieveResult knowledgeRetrieveResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下用户信息，进行风险评估：\n\n");

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
                    sb.append("\n");
                }
            });
            sb.append("\n");
        }

        if (inputResult.getEmotionStatisticsResult() != null) {
            sb.append("## 情绪统计\n");
            if (inputResult.getEmotionStatisticsResult().getBaseInfo() != null
                    && inputResult.getEmotionStatisticsResult().getBaseInfo().getDominantEmotion() != null) {
                sb.append("- 主导情绪：").append(inputResult.getEmotionStatisticsResult().getBaseInfo().getDominantEmotion().getLabel()).append("\n");
            }
            if (inputResult.getEmotionStatisticsResult().getTrend() != null) {
                sb.append("- 情绪趋势：").append(inputResult.getEmotionStatisticsResult().getTrend().getEmotionTrend()).append("\n");
                if (inputResult.getEmotionStatisticsResult().getTrend().getNegativeChangeCount() != null) {
                    sb.append("- 负向恶化次数：").append(inputResult.getEmotionStatisticsResult().getTrend().getNegativeChangeCount()).append("\n");
                }
            }
            sb.append("\n");
        }

        if (knowledgeRetrieveResult != null) {
            if (knowledgeRetrieveResult.getDiagnosisReferencePrompt() != null && !knowledgeRetrieveResult.getDiagnosisReferencePrompt().isBlank()) {
                sb.append("## 诊断标准参考\n").append(knowledgeRetrieveResult.getDiagnosisReferencePrompt()).append("\n\n");
            }
            if (knowledgeRetrieveResult.getInterventionReferencePrompt() != null && !knowledgeRetrieveResult.getInterventionReferencePrompt().isBlank()) {
                sb.append("## 干预方案参考\n").append(knowledgeRetrieveResult.getInterventionReferencePrompt()).append("\n\n");
            }
        }

        HistoryDiagnosisSummaryResult historySummary = inputResult.getHistoryDiagnosisSummaryResult();
        if (historySummary != null) {
            if (historySummary.getFirstDiagnosisPrompt() != null && !historySummary.getFirstDiagnosisPrompt().isBlank()) {
                sb.append("## 历史诊断摘要\n").append(historySummary.getFirstDiagnosisPrompt()).append("\n\n");
            }
            if (historySummary.getRiskPointsList() != null && !historySummary.getRiskPointsList().isEmpty()) {
                sb.append("## 历史风险点\n");
                historySummary.getRiskPointsList().forEach(rp -> sb.append("- ").append(rp).append("\n"));
                sb.append("\n");
            }
        }

        sb.append("请审慎评估并输出风险评估结果，包括情绪风险等级、情绪调节建议、自伤风险等级、自杀风险等级、风险细节描述、是否需要人工干预和是否触发危机预警。\n");
        return sb.toString();
    }
}