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
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.*;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.server.ai.model.diagnosis.process.ComprehensiveDiagnosisProcessModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 诊断处理侧-情绪综合分析节点
 * 分析整体情绪趋势、负向情绪细分占比和正向情绪细分占比
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComprehensiveDiagnosisNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "comprehensiveDiagnosisNode";

    private final ComprehensiveDiagnosisProcessModel comprehensiveDiagnosisProcessModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断处理侧-情绪综合分析-开始");

        Optional<DiagnosisDataRequest> diagnosisDataRequestOpt = state.value(DiagnosisDataRequest.NAME);
        if (diagnosisDataRequestOpt.isEmpty()) {
            log.error("诊断处理侧-情绪综合分析-诊断数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_NOT_EXIST);
        }
        DiagnosisDataRequest diagnosisDataRequest = diagnosisDataRequestOpt.get();
        InputResult inputResult = diagnosisDataRequest.getInputResult();
        KnowledgeRetrieveResult knowledgeRetrieveResult = diagnosisDataRequest.getKnowledgeRetrieveResult();

        String userPrompt = buildUserPrompt(inputResult, knowledgeRetrieveResult);
        log.debug("诊断处理侧-情绪综合分析-构建用户提示词完成，提示词：{}", userPrompt);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
        ComprehensiveDiagnosisProcessModel.EmotionComprehensiveResult result = comprehensiveDiagnosisProcessModel.callForResult(chatModel, userPrompt, aiNodeConfig, config);
        log.debug("诊断处理侧-情绪综合分析-模型返回结果：{}", result);

        if (result == null) {
            log.error("诊断处理侧-情绪综合分析-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_PROCESS_RESULT_PARSE_FAILED);
        }

        Optional<DiagnosisData> diagnosisDataOpt = state.value(DiagnosisData.NAME);
        if (diagnosisDataOpt.isEmpty()) {
            log.error("诊断处理侧-情绪综合分析-诊断结果数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_RESULT_NOT_EXIST);
        }
        DiagnosisData diagnosisData = diagnosisDataOpt.get();

        EmotionStatisticsResult emotionStats = inputResult.getEmotionStatisticsResult();
        fillCoreEmotionDimension(diagnosisData, emotionStats);
        fillEmotionDistributionDimension(diagnosisData, emotionStats);
        fillEmotionDynamicDimension(diagnosisData, emotionStats);
        log.debug("诊断处理侧-情绪综合分析-填充情绪维度数据完成，diagnosisData：{}", diagnosisData);

        diagnosisData.setNegativeEmotionDetail(convertToBigDecimalMap(result.getNegativeEmotionDetail()));
        diagnosisData.setPositiveEmotionDetail(convertToBigDecimalMap(result.getPositiveEmotionDetail()));

        log.info("诊断处理侧-情绪综合分析-完成，负向细分：{}，正向细分：{}",
                result.getNegativeEmotionDetail(), result.getPositiveEmotionDetail());
        return Map.of();
    }

    private String buildUserPrompt(InputResult inputResult, KnowledgeRetrieveResult knowledgeRetrieveResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下用户情绪统计数据，进行情绪综合分析：\n\n");

        EmotionStatisticsResult emotionStats = inputResult.getEmotionStatisticsResult();
        if (emotionStats != null) {
            if (emotionStats.getHasValidData() != null) {
                sb.append("## 是否存在有效情绪数据\n").append(emotionStats.getHasValidData() ? "是" : "否").append("\n\n");
            }

            if (emotionStats.getValidRoundNum() != null) {
                sb.append("## 有效情绪轮次数\n").append(emotionStats.getValidRoundNum()).append("\n\n");
            }

            BaseInfo baseInfo = emotionStats.getBaseInfo();
            if (baseInfo != null) {
                if (baseInfo.getDominantEmotion() != null) {
                    sb.append("## 主导情绪\n")
                            .append(baseInfo.getDominantEmotion().getLabel())
                            .append("（占比：").append(baseInfo.getDominantEmotion().getRatio()).append("）\n\n");
                }

                if (baseInfo.getEmotionDistribution() != null && !baseInfo.getEmotionDistribution().isEmpty()) {
                    sb.append("## 主情绪标签分布\n");
                    for (EmotionDistributionItem item : baseInfo.getEmotionDistribution()) {
                        sb.append("- ").append(item.getLabel())
                                .append("：占比").append(item.getRatio())
                                .append("，出现").append(item.getCount()).append("轮")
                                .append("，平均强度").append(item.getAvgIntensity())
                                .append("，峰值强度").append(item.getPeakIntensity())
                                .append("\n");
                    }
                    sb.append("\n");
                }

                if (baseInfo.getSubEmotionDistribution() != null && !baseInfo.getSubEmotionDistribution().isEmpty()) {
                    sb.append("## 细分情绪标签分布\n");
                    for (SubEmotionDistributionItem item : baseInfo.getSubEmotionDistribution()) {
                        sb.append("- ").append(item.getLabel())
                                .append("：占比").append(item.getRatio())
                                .append("，出现").append(item.getCount()).append("轮")
                                .append("\n");
                    }
                    sb.append("\n");
                }

                if (baseInfo.getAvgPositiveRatio() != null) {
                    sb.append("## 正负向情绪占比\n");
                    sb.append("- 平均正向情绪占比：").append(baseInfo.getAvgPositiveRatio()).append("\n");
                    sb.append("- 平均中性情绪占比：").append(baseInfo.getAvgNeutralRatio()).append("\n");
                    sb.append("- 平均负向情绪占比：").append(baseInfo.getAvgNegativeRatio()).append("\n");
                    if (baseInfo.getAvgConfidence() != null) {
                        sb.append("- 平均识别置信度：").append(baseInfo.getAvgConfidence()).append("\n");
                    }
                    sb.append("\n");
                }
            }

            if (emotionStats.getQuantitative() != null) {
                sb.append("## 量化指标\n");
                if (emotionStats.getQuantitative().getP() != null) {
                    sb.append("- 愉悦度(P)均值：").append(emotionStats.getQuantitative().getP().getAvg()).append("\n");
                }
                if (emotionStats.getQuantitative().getA() != null) {
                    sb.append("- 唤醒度(A)均值：").append(emotionStats.getQuantitative().getA().getAvg()).append("\n");
                }
                if (emotionStats.getQuantitative().getD() != null) {
                    sb.append("- 支配度(D)均值：").append(emotionStats.getQuantitative().getD().getAvg()).append("\n");
                }
                if (emotionStats.getQuantitative().getIntensity() != null) {
                    sb.append("- 全局情绪强度均值：").append(emotionStats.getQuantitative().getIntensity().getAvg()).append("\n");
                }
                sb.append("\n");
            }

            Trend trend = emotionStats.getTrend();
            if (trend != null) {
                sb.append("## 情绪动态趋势\n");
                sb.append("- 整体趋势：").append(trend.getEmotionTrend()).append("\n");
                if (trend.getEmotionStableRounds() != null) {
                    sb.append("- 平稳轮次数：").append(trend.getEmotionStableRounds()).append("\n");
                }
                if (trend.getEmotionStabilityScore() != null) {
                    sb.append("- 稳定性得分：").append(trend.getEmotionStabilityScore()).append("\n");
                }
                if (trend.getEmotionChangeCount() != null) {
                    sb.append("- 情绪变化总次数：").append(trend.getEmotionChangeCount()).append("\n");
                }
                if (trend.getNegativeChangeCount() != null) {
                    sb.append("- 负向恶化次数：").append(trend.getNegativeChangeCount()).append("\n");
                }
                sb.append("\n");
            }
        }

        if (inputResult.getHistoryDiagnosisSummaryResult() != null
                && inputResult.getHistoryDiagnosisSummaryResult().getEmotionTrend() != null
                && inputResult.getHistoryDiagnosisSummaryResult().getEmotionTrend().getBasicInfo() != null) {
            sb.append("## 历史情绪趋势\n")
                    .append(inputResult.getHistoryDiagnosisSummaryResult().getEmotionTrend().getBasicInfo()).append("\n\n");
        }

        sb.append("请分析并输出情绪综合分析结果，包括整体情绪趋势、负向情绪细分占比和正向情绪细分占比。\n");
        return sb.toString();
    }

    @Override
    public Object inputSummary(OverAllState state) {
        return state.value(DiagnosisDataRequest.NAME).orElse(null);
    }

    @Override
    public Object outputSummary(OverAllState state) {
        Optional<DiagnosisData> dataOpt = state.value(DiagnosisData.NAME);
        return dataOpt.map(data -> Map.ofEntries(
                Map.entry("coreEmotionLabel", data.getCoreEmotionLabel()),
                Map.entry("coreEmotionConfAvg", data.getCoreEmotionConfAvg()),
                Map.entry("coreEmotionIntensityScore", data.getCoreEmotionIntensityScore()),
                Map.entry("coreEmotion", data.getCoreEmotion()),
                Map.entry("secondaryEmotion", data.getSecondaryEmotion()),
                Map.entry("negativeEmotionRatio", data.getNegativeEmotionRatio()),
                Map.entry("positiveEmotionRatio", data.getPositiveEmotionRatio()),
                Map.entry("neutralEmotionRatio", data.getNeutralEmotionRatio()),
                Map.entry("negativeEmotionDetail", data.getNegativeEmotionDetail()),
                Map.entry("positiveEmotionDetail", data.getPositiveEmotionDetail()),
                Map.entry("emotionTrend", data.getEmotionTrend()),
                Map.entry("emotionPeakRound", data.getEmotionPeakRound()),
                Map.entry("emotionValleyRound", data.getEmotionValleyRound()),
                Map.entry("emotionFluctuationAmplitude", data.getEmotionFluctuationAmplitude()),
                Map.entry("emotionStableRounds", data.getEmotionStableRounds()),
                Map.entry("emotionStabilityScore", data.getEmotionStabilityScore()),
                Map.entry("avgP", data.getAvgP()),
                Map.entry("stdP", data.getStdP()),
                Map.entry("avgA", data.getAvgA()),
                Map.entry("stdA", data.getStdA()),
                Map.entry("avgD", data.getAvgD()),
                Map.entry("stdD", data.getStdD())
        )).orElse(null);
    }

    private Map<String, BigDecimal> convertToBigDecimalMap(Map<String, Double> source) {
        if (source == null) {
            return null;
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, BigDecimal.valueOf(value)));
        return result;
    }

    private void fillCoreEmotionDimension(DiagnosisData diagnosisData, EmotionStatisticsResult emotionStats) {
        if (emotionStats == null || emotionStats.getBaseInfo() == null) {
            return;
        }
        BaseInfo baseInfo = emotionStats.getBaseInfo();

        if (baseInfo.getDominantEmotion() != null) {
            diagnosisData.setCoreEmotionLabel(baseInfo.getDominantEmotion().getLabel());
            diagnosisData.setCoreEmotionConfAvg(baseInfo.getDominantEmotion().getAvgConfidence());
        }

        if (emotionStats.getQuantitative() != null && emotionStats.getQuantitative().getIntensity() != null) {
            diagnosisData.setCoreEmotionIntensityScore(emotionStats.getQuantitative().getIntensity().getAvg());
        }

        if (baseInfo.getSubEmotionDistribution() != null && !baseInfo.getSubEmotionDistribution().isEmpty()) {
            Map<String, BigDecimal> secondaryEmotion = new LinkedHashMap<>();
            for (SubEmotionDistributionItem item : baseInfo.getSubEmotionDistribution()) {
                secondaryEmotion.put(item.getLabel(), item.getAvgConfidence());
            }
            diagnosisData.setSecondaryEmotion(secondaryEmotion);
        }

        if (baseInfo.getEmotionDistribution() != null && !baseInfo.getEmotionDistribution().isEmpty()) {
            Map<String, BigDecimal> coreEmotion = new LinkedHashMap<>();
            for (EmotionDistributionItem item : baseInfo.getEmotionDistribution()) {
                coreEmotion.put(item.getLabel(), item.getAvgConfidence());
            }
            diagnosisData.setCoreEmotion(coreEmotion);
        }

    }

    private void fillEmotionDistributionDimension(DiagnosisData diagnosisData, EmotionStatisticsResult emotionStats) {
        if (emotionStats == null || emotionStats.getBaseInfo() == null) {
            return;
        }
        BaseInfo baseInfo = emotionStats.getBaseInfo();
        diagnosisData.setNegativeEmotionRatio(baseInfo.getAvgNegativeRatio());
        diagnosisData.setPositiveEmotionRatio(baseInfo.getAvgPositiveRatio());
        diagnosisData.setNeutralEmotionRatio(baseInfo.getAvgNeutralRatio());
    }

    private void fillEmotionDynamicDimension(DiagnosisData diagnosisData, EmotionStatisticsResult emotionStats) {
        if (emotionStats == null) {
            return;
        }

        Trend trend = emotionStats.getTrend();
        if (trend != null) {
            String trendStr = trend.getEmotionTrend();
            if (trendStr != null) {
                int trendValue = switch (trendStr) {
                    case EmotionStatisticsResult.TREND_RISING -> EmotionDiagnosis.EMOTION_TREND_UP;
                    case EmotionStatisticsResult.TREND_FALLING -> EmotionDiagnosis.EMOTION_TREND_DOWN;
                    case EmotionStatisticsResult.TREND_STABLE -> EmotionDiagnosis.EMOTION_TREND_STABLE;
                    default -> EmotionDiagnosis.EMOTION_TREND_UNDETERMINED;
                };
                diagnosisData.setEmotionTrend(trendValue);
            }
            diagnosisData.setEmotionStableRounds(trend.getEmotionStableRounds());
            diagnosisData.setEmotionStabilityScore(trend.getEmotionStabilityScore());
        }

        Quantitative quantitative = emotionStats.getQuantitative();
        if (quantitative != null) {
            if (quantitative.getIntensity() != null) {
                IntensityStat intensity = quantitative.getIntensity();
                diagnosisData.setEmotionPeakRound(intensity.getPeakRound());
                diagnosisData.setEmotionValleyRound(intensity.getValleyRound());
                diagnosisData.setEmotionFluctuationAmplitude(intensity.getWaveRange());
            }
            if (quantitative.getP() != null) {
                diagnosisData.setAvgP(quantitative.getP().getAvg());
                diagnosisData.setStdP(quantitative.getP().getStd());
            }
            if (quantitative.getA() != null) {
                diagnosisData.setAvgA(quantitative.getA().getAvg());
                diagnosisData.setStdA(quantitative.getA().getStd());
            }
            if (quantitative.getD() != null) {
                diagnosisData.setAvgD(quantitative.getD().getAvg());
                diagnosisData.setStdD(quantitative.getD().getStd());
            }
        }
    }
}