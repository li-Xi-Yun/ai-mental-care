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
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.factory.InjectChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

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
public class InterventionSuggestionNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "interventionSuggestionNode";

    private final InterventionSuggestionProcessModel interventionSuggestionProcessModel;
    @InjectChatModel(ChatModelType.DEEP_SEEK)
    private ChatModel chatModel;

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

        String userPrompt = buildUserPrompt(inputResult, knowledgeRetrieveResult);
        log.info("诊断处理侧-干预建议生成-构建用户提示词完成");

        InterventionSuggestionProcessModel.InterventionSuggestionResult result = interventionSuggestionProcessModel.callForResult(chatModel, userPrompt);

        if (result == null) {
            log.error("诊断处理侧-干预建议生成-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_PROCESS_RESULT_PARSE_FAILED);
        }

        Optional<DiagnosisData> diagnosisDataOpt = state.value(DiagnosisData.NAME);
        if (diagnosisDataOpt.isEmpty()) {
            log.error("诊断处理侧-干预建议生成-诊断结果数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_RESULT_NOT_EXIST);
        }
        DiagnosisData diagnosisData = diagnosisDataOpt.get();
        diagnosisData.setSelfHelpSuggestion(result.getSelfHelpSuggestion());
        diagnosisData.setSocialSupportSuggestion(result.getSocialSupportSuggestion());
        diagnosisData.setProfessionalInterveneSuggestion(result.getProfessionalInterveneSuggestion());
        diagnosisData.setSuggestionPriority(result.getSuggestionPriority());

        log.info("诊断处理侧-干预建议生成-完成，建议优先级：{}", result.getSuggestionPriority());
        return Map.of();
    }

    private String buildUserPrompt(InputResult inputResult, KnowledgeRetrieveResult knowledgeRetrieveResult) {
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
}