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
import org.lixiyun.server.ai.model.diagnosis.process.InterventionSuggestionProcessModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 诊断处理侧-干预建议生成节点
 * 根据评估结果生成自助调节建议、社会支持建议、专业干预建议，并确定建议优先级
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterventionSuggestionNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "interventionSuggestionNode";

    private final InterventionSuggestionProcessModel interventionSuggestionProcessModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断处理侧-干预建议生成-开始");

        Optional<DiagnosisDataRequest> diagnosisDataRequestOpt = state.value(DiagnosisDataRequest.NAME);
        if (diagnosisDataRequestOpt.isEmpty()) {
            log.error("诊断处理侧-干预建议生成-诊断数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_NOT_EXIST);
        }
        DiagnosisDataRequest diagnosisDataRequest = diagnosisDataRequestOpt.get();
        InputResult inputResult = diagnosisDataRequest.getInputResult();
        KnowledgeRetrieveResult knowledgeRetrieveResult = diagnosisDataRequest.getKnowledgeRetrieveResult();

        Optional<DiagnosisData> diagnosisDataOpt = state.value(DiagnosisData.NAME);
        if (diagnosisDataOpt.isEmpty()) {
            log.error("诊断处理侧-干预建议生成-诊断结果数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_RESULT_NOT_EXIST);
        }
        DiagnosisData diagnosisData = diagnosisDataOpt.get();

        String userPrompt = buildUserPrompt(inputResult, knowledgeRetrieveResult, diagnosisData);
        log.debug("诊断处理侧-干预建议生成-构建用户提示词完成，提示词：{}", userPrompt);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
        InterventionSuggestionProcessModel.InterventionSuggestionResult result = interventionSuggestionProcessModel.callForResult(chatModel, userPrompt, aiNodeConfig);
        log.debug("诊断处理侧-干预建议生成-模型返回结果：{}", result);

        if (result == null) {
            log.error("诊断处理侧-干预建议生成-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_PROCESS_RESULT_PARSE_FAILED);
        }
        diagnosisData.setSelfHelpSuggestion(result.getSelfHelpSuggestion());
        diagnosisData.setSocialSupportSuggestion(result.getSocialSupportSuggestion());
        diagnosisData.setProfessionalInterveneSuggestion(result.getProfessionalInterveneSuggestion());
        diagnosisData.setSuggestionPriority(result.getSuggestionPriority());
        log.debug("诊断处理侧-干预建议生成-写入诊断数据完成，结果：{}", result);

        log.info("诊断处理侧-干预建议生成-完成，建议优先级：{}", result.getSuggestionPriority());
        return Map.of();
    }

    private String buildUserPrompt(InputResult inputResult, KnowledgeRetrieveResult knowledgeRetrieveResult, DiagnosisData diagnosisData) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下用户信息，生成干预建议：\n\n");

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

        if (inputResult.getEmotionStatisticsResult() != null
                && inputResult.getEmotionStatisticsResult().getBaseInfo() != null
                && inputResult.getEmotionStatisticsResult().getBaseInfo().getDominantEmotion() != null) {
            sb.append("## 主导情绪\n")
                    .append(inputResult.getEmotionStatisticsResult().getBaseInfo().getDominantEmotion().getLabel())
                    .append("\n\n");
        }

        if (diagnosisData != null) {
            StringBuilder assessmentSb = new StringBuilder();
            if (diagnosisData.getPsychologicalState() != null && !diagnosisData.getPsychologicalState().isBlank()) {
                assessmentSb.append("- 心理状态评估：").append(diagnosisData.getPsychologicalState()).append("\n");
            }
            if (diagnosisData.getSymptomSummary() != null && !diagnosisData.getSymptomSummary().isBlank()) {
                assessmentSb.append("- 核心症状：").append(diagnosisData.getSymptomSummary()).append("\n");
            }
            if (diagnosisData.getSymptomDuration() != null && !diagnosisData.getSymptomDuration().isBlank()) {
                assessmentSb.append("- 症状持续时长：").append(diagnosisData.getSymptomDuration()).append("\n");
            }
            if (diagnosisData.getSocialFunctionImpact() != null && !diagnosisData.getSocialFunctionImpact().isBlank()) {
                assessmentSb.append("- 社会功能受损程度：").append(diagnosisData.getSocialFunctionImpact()).append("\n");
            }
            if (diagnosisData.getImpactDomains() != null && !diagnosisData.getImpactDomains().isBlank()) {
                assessmentSb.append("- 受影响领域：").append(diagnosisData.getImpactDomains()).append("\n");
            }
            if (diagnosisData.getEmotionRiskLevel() != null) {
                String[] riskLabels = {"低", "中", "高", "危急", "无法判断"};
                String riskLabel = diagnosisData.getEmotionRiskLevel() < riskLabels.length
                        ? riskLabels[diagnosisData.getEmotionRiskLevel()] : "未知";
                assessmentSb.append("- 情绪风险等级：").append(riskLabel)
                        .append("（编码=").append(diagnosisData.getEmotionRiskLevel()).append("）\n");
            }
            if (diagnosisData.getSelfHarmRiskLevel() != null) {
                assessmentSb.append("- 自伤风险等级编码：").append(diagnosisData.getSelfHarmRiskLevel()).append("\n");
            }
            if (diagnosisData.getSuicideRiskLevel() != null) {
                assessmentSb.append("- 自杀风险等级编码：").append(diagnosisData.getSuicideRiskLevel()).append("\n");
            }
            if (diagnosisData.getRiskDetail() != null && !diagnosisData.getRiskDetail().isBlank()) {
                assessmentSb.append("- 风险细节：").append(diagnosisData.getRiskDetail()).append("\n");
            }
            if (diagnosisData.getNeedManualIntervene() != null) {
                assessmentSb.append("- 是否需要人工干预：").append(diagnosisData.getNeedManualIntervene() == 1 ? "是" : "否").append("\n");
            }
            if (diagnosisData.getCrisisWarning() != null) {
                assessmentSb.append("- 是否触发危机预警：").append(diagnosisData.getCrisisWarning() == 1 ? "是" : "否").append("\n");
            }
            if (diagnosisData.getProtectiveFactors() != null && !diagnosisData.getProtectiveFactors().isBlank()) {
                assessmentSb.append("- 保护性因素：").append(diagnosisData.getProtectiveFactors()).append("\n");
            }
            if (diagnosisData.getCopingStyle() != null && !diagnosisData.getCopingStyle().isBlank()) {
                assessmentSb.append("- 应对方式：").append(diagnosisData.getCopingStyle()).append("\n");
            }
            if (diagnosisData.getSocialSupportLevel() != null) {
                assessmentSb.append("- 社会支持水平编码：").append(diagnosisData.getSocialSupportLevel()).append("\n");
            }
            if (assessmentSb.length() > 0) {
                sb.append("## 评估结果\n").append(assessmentSb).append("\n");
            }
        }

        if (knowledgeRetrieveResult != null && knowledgeRetrieveResult.getInterventionReferencePrompt() != null
                && !knowledgeRetrieveResult.getInterventionReferencePrompt().isBlank()) {
            sb.append("## 干预方案参考\n").append(knowledgeRetrieveResult.getInterventionReferencePrompt()).append("\n\n");
        }

        if (inputResult.getHistoryDiagnosisSummaryResult() != null
                && inputResult.getHistoryDiagnosisSummaryResult().getFirstDiagnosisPrompt() != null
                && !inputResult.getHistoryDiagnosisSummaryResult().getFirstDiagnosisPrompt().isBlank()) {
            sb.append("## 历史诊断摘要\n")
                    .append(inputResult.getHistoryDiagnosisSummaryResult().getFirstDiagnosisPrompt()).append("\n\n");
        }

        sb.append("请生成并输出干预建议结果，包括自助调节建议、社会支持建议、专业干预建议和建议优先级。\n");
        return sb.toString();
    }

    @Override
    public Object inputSummary(OverAllState state) {
        Map<String, Object> input = new LinkedHashMap<>();
        state.value(DiagnosisDataRequest.NAME).ifPresent(req -> input.put("diagnosisDataRequest", req));
        state.value(DiagnosisData.NAME).ifPresent(data -> input.put("diagnosisData", data));
        return input.isEmpty() ? null : input;
    }

    @Override
    public Object outputSummary(OverAllState state) {
        Optional<DiagnosisData> dataOpt = state.value(DiagnosisData.NAME);
        return dataOpt.map(data -> Map.of(
                "selfHelpSuggestion", (Object) data.getSelfHelpSuggestion(),
                "socialSupportSuggestion", (Object) data.getSocialSupportSuggestion(),
                "professionalInterveneSuggestion", (Object) data.getProfessionalInterveneSuggestion(),
                "suggestionPriority", (Object) data.getSuggestionPriority()
        )).orElse(null);
    }
}