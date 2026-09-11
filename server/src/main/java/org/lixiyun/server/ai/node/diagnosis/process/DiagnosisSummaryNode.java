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
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.BaseInfo;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.EmotionStatisticsResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.Trend;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.CoreInfoExtractResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.server.ai.model.diagnosis.process.DiagnosisSummaryProcessModel;
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
 * 诊断处理侧-诊断书生成节点
 * 综合所有输入数据，生成诊断书核心内容、核心情绪标签、核心情绪平均置信度和核心情绪强度分值
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisSummaryNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "diagnosisSummaryNode";

    private final DiagnosisSummaryProcessModel diagnosisSummaryProcessModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断处理侧-诊断书生成-开始");

        Optional<DiagnosisDataRequest> diagnosisDataRequestOpt = state.value(DiagnosisDataRequest.NAME);
        if (diagnosisDataRequestOpt.isEmpty()) {
            log.error("诊断处理侧-诊断书生成-诊断数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_NOT_EXIST);
        }
        DiagnosisDataRequest diagnosisDataRequest = diagnosisDataRequestOpt.get();
        InputResult inputResult = diagnosisDataRequest.getInputResult();
        KnowledgeRetrieveResult knowledgeRetrieveResult = diagnosisDataRequest.getKnowledgeRetrieveResult();

        Optional<DiagnosisData> existingDiagnosisDataOpt = state.value(DiagnosisData.NAME);
        DiagnosisData existingDiagnosisData = existingDiagnosisDataOpt.orElse(null);

        String userPrompt = buildUserPrompt(inputResult, knowledgeRetrieveResult, existingDiagnosisData);
        log.debug("诊断处理侧-诊断书生成-构建用户提示词完成，提示词：{}", userPrompt);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
        DiagnosisSummaryProcessModel.DiagnosisSummaryResult result =
                diagnosisSummaryProcessModel.callForResult(chatModel, userPrompt, aiNodeConfig);
        log.debug("诊断处理侧-诊断书生成-模型返回结果：{}", result);

        if (result == null) {
            log.error("诊断处理侧-诊断书生成-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_PROCESS_RESULT_PARSE_FAILED);
        }

        Optional<DiagnosisData> diagnosisDataOpt = state.value(DiagnosisData.NAME);
        if (diagnosisDataOpt.isEmpty()) {
            log.error("诊断处理侧-诊断书生成-诊断结果数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_RESULT_NOT_EXIST);
        }
        DiagnosisData diagnosisData = diagnosisDataOpt.get();
        diagnosisData.setDiagnosisContent(result.getDiagnosisContent());
        diagnosisData.setCoreEmotionLabel(result.getCoreEmotionLabel());
        diagnosisData.setCoreEmotionConfAvg(result.getCoreEmotionConfAvg());
        diagnosisData.setCoreEmotionIntensityScore(result.getCoreEmotionIntensityScore());
        log.debug("诊断处理侧-诊断书生成-写入诊断数据完成，结果：{}", result);

        log.info("诊断处理侧-诊断书生成-完成，核心情绪标签：{}，置信度：{}，强度：{}",
                result.getCoreEmotionLabel(), result.getCoreEmotionConfAvg(), result.getCoreEmotionIntensityScore());
        return Map.of();
    }

    private String buildUserPrompt(InputResult inputResult, KnowledgeRetrieveResult knowledgeRetrieveResult, DiagnosisData diagnosisData) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下用户信息，生成诊断书核心内容总结，并提取核心情绪标签、核心情绪平均置信度和核心情绪强度分值：\n\n");

        if (diagnosisData != null) {
            boolean hasUpstreamResult = false;
            StringBuilder upstreamSb = new StringBuilder();

            if (diagnosisData.getPsychologicalState() != null && !diagnosisData.getPsychologicalState().isBlank()) {
                upstreamSb.append("- 心理状态评估：").append(diagnosisData.getPsychologicalState()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getSymptomSummary() != null && !diagnosisData.getSymptomSummary().isBlank()) {
                upstreamSb.append("- 核心症状总结：").append(diagnosisData.getSymptomSummary()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getSymptomTags() != null && !diagnosisData.getSymptomTags().isBlank()) {
                upstreamSb.append("- 症状标签：").append(diagnosisData.getSymptomTags()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getSymptomDuration() != null && !diagnosisData.getSymptomDuration().isBlank()) {
                upstreamSb.append("- 症状持续时长：").append(diagnosisData.getSymptomDuration()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getOnsetPattern() != null && !diagnosisData.getOnsetPattern().isBlank()) {
                upstreamSb.append("- 发作模式：").append(diagnosisData.getOnsetPattern()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getSocialFunctionImpact() != null && !diagnosisData.getSocialFunctionImpact().isBlank()) {
                upstreamSb.append("- 社会功能受损程度：").append(diagnosisData.getSocialFunctionImpact()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getImpactDomains() != null && !diagnosisData.getImpactDomains().isBlank()) {
                upstreamSb.append("- 受影响领域：").append(diagnosisData.getImpactDomains()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getDailyLifeInfluence() != null && !diagnosisData.getDailyLifeInfluence().isBlank()) {
                upstreamSb.append("- 日常生活影响：").append(diagnosisData.getDailyLifeInfluence()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getProtectiveFactors() != null && !diagnosisData.getProtectiveFactors().isBlank()) {
                upstreamSb.append("- 保护性因素：").append(diagnosisData.getProtectiveFactors()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getCopingStyle() != null && !diagnosisData.getCopingStyle().isBlank()) {
                upstreamSb.append("- 应对方式：").append(diagnosisData.getCopingStyle()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getSocialSupportLevel() != null) {
                String[] levels = {"良好", "一般", "较差", "匮乏", "无法判断"};
                String levelStr = diagnosisData.getSocialSupportLevel() < levels.length
                        ? levels[diagnosisData.getSocialSupportLevel()] : "无法判断";
                upstreamSb.append("- 社会支持水平：").append(levelStr).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getCoreTriggerScene() != null && !diagnosisData.getCoreTriggerScene().isBlank()) {
                upstreamSb.append("- 核心触发场景：").append(diagnosisData.getCoreTriggerScene()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getCoreTriggerKeywords() != null && !diagnosisData.getCoreTriggerKeywords().isBlank()) {
                upstreamSb.append("- 核心触发关键词：").append(diagnosisData.getCoreTriggerKeywords()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getEmotionRiskLevel() != null) {
                String[] riskLevels = {"低", "中", "高", "危急", "无法判断"};
                String riskStr = diagnosisData.getEmotionRiskLevel() < riskLevels.length
                        ? riskLevels[diagnosisData.getEmotionRiskLevel()] : "无法判断";
                upstreamSb.append("- 情绪风险等级：").append(riskStr).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getRiskDetail() != null && !diagnosisData.getRiskDetail().isBlank()) {
                upstreamSb.append("- 风险细节：").append(diagnosisData.getRiskDetail()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getSelfHelpSuggestion() != null && !diagnosisData.getSelfHelpSuggestion().isBlank()) {
                upstreamSb.append("- 自助调节建议：").append(diagnosisData.getSelfHelpSuggestion()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getSocialSupportSuggestion() != null && !diagnosisData.getSocialSupportSuggestion().isBlank()) {
                upstreamSb.append("- 社会支持建议：").append(diagnosisData.getSocialSupportSuggestion()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getProfessionalInterveneSuggestion() != null && !diagnosisData.getProfessionalInterveneSuggestion().isBlank()) {
                upstreamSb.append("- 专业干预建议：").append(diagnosisData.getProfessionalInterveneSuggestion()).append("\n");
                hasUpstreamResult = true;
            }
            if (diagnosisData.getEmotionAdjustSuggestion() != null && !diagnosisData.getEmotionAdjustSuggestion().isBlank()) {
                upstreamSb.append("- 情绪调节建议：").append(diagnosisData.getEmotionAdjustSuggestion()).append("\n");
                hasUpstreamResult = true;
            }

            if (hasUpstreamResult) {
                sb.append("## 上游分析结论（优先引用）\n").append(upstreamSb).append("\n");
            }
        }

        CoreInfoExtractResult coreInfo = inputResult.getCoreInfoExtractResult();
        if (coreInfo != null) {
            if (coreInfo.getCoreAppeal() != null && !coreInfo.getCoreAppeal().isBlank()) {
                sb.append("## 核心诉求\n").append(coreInfo.getCoreAppeal()).append("\n\n");
            }
            if (coreInfo.getBackgroundSummary() != null && !coreInfo.getBackgroundSummary().isBlank()) {
                sb.append("## 背景信息\n").append(coreInfo.getBackgroundSummary()).append("\n\n");
            }
        }

        EmotionStatisticsResult emotionStats = inputResult.getEmotionStatisticsResult();
        if (emotionStats != null) {
            if (emotionStats.getHasValidData() != null) {
                sb.append("## 是否存在有效情绪数据\n").append(emotionStats.getHasValidData() ? "是" : "否").append("\n\n");
            }

            BaseInfo baseInfo = emotionStats.getBaseInfo();
            if (baseInfo != null) {
                if (baseInfo.getDominantEmotion() != null) {
                    sb.append("## 主导情绪\n")
                            .append(baseInfo.getDominantEmotion().getLabel())
                            .append("（占比：").append(baseInfo.getDominantEmotion().getRatio()).append("）\n\n");
                }

                if (baseInfo.getAvgPositiveRatio() != null) {
                    sb.append("## 正负向情绪占比\n");
                    sb.append("- 平均正向情绪占比：").append(baseInfo.getAvgPositiveRatio()).append("\n");
                    sb.append("- 平均中性情绪占比：").append(baseInfo.getAvgNeutralRatio()).append("\n");
                    sb.append("- 平均负向情绪占比：").append(baseInfo.getAvgNegativeRatio()).append("\n\n");
                }
            }

            Trend trend = emotionStats.getTrend();
            if (trend != null) {
                sb.append("## 情绪动态趋势\n");
                sb.append("- 整体趋势：").append(trend.getEmotionTrend()).append("\n");
                if (trend.getEmotionStabilityScore() != null) {
                    sb.append("- 稳定性得分：").append(trend.getEmotionStabilityScore()).append("\n");
                }
                if (trend.getNegativeChangeCount() != null) {
                    sb.append("- 负向恶化次数：").append(trend.getNegativeChangeCount()).append("\n");
                }
                sb.append("\n");
            }

            if (emotionStats.getQuantitative() != null) {
                sb.append("## 量化指标\n");
                if (emotionStats.getQuantitative().getP() != null) {
                    sb.append("- 愉悦度(P)均值：").append(emotionStats.getQuantitative().getP().getAvg()).append("\n");
                }
                if (emotionStats.getQuantitative().getA() != null) {
                    sb.append("- 唤醒度(A)均值：").append(emotionStats.getQuantitative().getA().getAvg()).append("\n");
                }
                if (emotionStats.getQuantitative().getIntensity() != null) {
                    sb.append("- 全局情绪强度均值：").append(emotionStats.getQuantitative().getIntensity().getAvg()).append("\n");
                }
                sb.append("\n");
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

        if (knowledgeRetrieveResult != null) {
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

        sb.append("请综合以上所有信息，生成诊断书核心内容总结，并提取核心情绪标签、核心情绪平均置信度和核心情绪强度分值。\n");
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
                "diagnosisContent", (Object) data.getDiagnosisContent(),
                "coreEmotionLabel", (Object) data.getCoreEmotionLabel(),
                "coreEmotionConfAvg", (Object) data.getCoreEmotionConfAvg(),
                "coreEmotionIntensityScore", (Object) data.getCoreEmotionIntensityScore()
        )).orElse(null);
    }
}