package org.lixiyun.server.ai.node.diagnosis.input;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.RoundEffectiveLevel;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.SessionCleanResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.*;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 输入侧-情绪数据处理节点
 * <p>
 * 处理流程：
 * <ol>
 *   <li><b>筛选有效情绪数据</b>：遍历结构化轮次单元集合，筛选轮次标记为非无效且情绪置信度达到阈值的轮次</li>
 *   <li><b>数据集空值判断</b>：若有效数据集为空则直接结束；否则进入统计环节</li>
 *   <li><b>多维度并行统计运算</b>：六路任务并行开展情绪指标统计</li>
 *   <li><b>数据封装与返回</b>：将全部统计结果组装为 {@link EmotionStatisticsResult} 对外输出</li>
 * </ol>
 * <p>
 * 六路统计维度：
 * <ul>
 *   <li>主情感标签统计：频次、占比、加权平均强度、峰值强度</li>
 *   <li>子情感标签统计：频次、占比</li>
 *   <li>正负向情绪分类统计：正向/中性/负向占比（置信度加权平均）</li>
 *   <li>PAD三维特征统计：加权均值、极值、加权标准差、波动区间、最新取值</li>
 *   <li>情绪烈度指标统计：加权均值、峰值/谷值及轮次、波动幅度</li>
 *   <li>时序趋势采集：趋势判断、峰值/谷值轮次、波动幅度、平稳相邻对数、稳定性得分、标签变化次数、负向恶化次数</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-10 15:35
 * @see EmotionStatisticsResult
 * @see BaseInfo
 * @see Quantitative
 * @see Trend
 */
@Slf4j
@Component
public class EmotionStatisticsNode implements NodeActionWithConfig {

    /**
     * 节点名称标识
     */
    public static final String NODE_NAME = "emotionStatisticsNode";
    /**
     * 情绪置信度筛选阈值，低于此值的轮次不纳入统计
     */
    private static final BigDecimal CONFIDENCE_THRESHOLD = new BigDecimal("0.5");
    /**
     * BigDecimal 运算保留小数位数
     */
    private static final int SCALE = 4;
    /**
     * 趋势判断阈值：前后半段P维度均值差绝对值大于此值判定为上升/下降，否则为平稳
     */
    private static final BigDecimal TREND_THRESHOLD = new BigDecimal("0.1");
    /**
     * 平稳相邻对判定阈值：相邻两轮强度差值绝对值小于此值则记为平稳
     */
    private static final BigDecimal STABLE_DIFF_THRESHOLD = new BigDecimal("0.1");
    /**
     * 情绪强度上界，用于初始化最小值搜索
     */
    private static final BigDecimal INTENSITY_UPPER_BOUND = new BigDecimal("2");

    @Autowired
    @Qualifier("diagnosisThreadPoolTaskExecutor")
    private ThreadPoolTaskExecutor diagnosisExecutor;

    /**
     * 执行情绪数据处理流程
     * <p>
     * 从 {@link OverAllState} 中获取输入侧结果，筛选有效情绪轮次后执行多维度统计，
     * 最终将 {@link EmotionStatisticsResult} 写回 {@link InputResult}。
     *
     * @param state  全局状态，包含 {@link InputResult}
     * @param config 运行配置
     * @return 空Map（结果通过 state 传递）
     * @throws BusinessException 当输入结果或清洗结果为空时抛出
     */
    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("输入侧-情绪数据处理-开始");

        Optional<InputResult> inputResultOpt = state.value(InputResult.NAME);
        if (inputResultOpt.isEmpty()) {
            log.error("输入侧-情绪数据处理-输入侧流程聚合结果为空");
            throw new BusinessException(ConversationExceptionEnum.INPUT_RESULT_NOT_EXIST);
        }
        InputResult inputResult = inputResultOpt.get();
        SessionCleanResult sessionCleanResult = inputResult.getSessionCleanResult();
        if (sessionCleanResult == null || sessionCleanResult.getRoundEffectiveLevelList() == null) {
            log.error("输入侧-情绪数据处理-清洗结果为空");
            throw new BusinessException(ConversationExceptionEnum.SESSION_CLEAN_RESULT_NOT_EXIST);
        }

        List<RoundEffectiveLevel> rounds = sessionCleanResult.getRoundEffectiveLevelList();

        List<RoundEffectiveLevel> validRounds = rounds.stream()
                .filter(r -> r.getLevel() != null && r.getLevel() < SessionCleanResult.LEVEL_INVALID)
                .filter(r -> r.getEmotionAnalysis() != null)
                .filter(r -> r.getEmotionAnalysis().getEmotionConfidence() != null
                        && bd(r.getEmotionAnalysis().getEmotionConfidence())
                        .compareTo(CONFIDENCE_THRESHOLD) >= 0)
                .collect(Collectors.toList());

        EmotionStatisticsResult result = null;
        if (validRounds.isEmpty()) {
            log.info("输入侧-情绪数据处理-无有效情绪数据");
            // todo 结束所有流程
        } else {
            log.info("输入侧-情绪数据处理-有效情绪轮次数：{}", validRounds.size());
            result = buildStatisticsResult(validRounds);
        }

        inputResult.setEmotionStatisticsResult(result);
        log.info("输入侧-情绪数据处理-完成");
        return Map.of();
    }

    /**
     * 组装完整情绪统计数据包
     * <p>
     * 分别构建基础情绪分布 {@link BaseInfo}、量化指标 {@link Quantitative}、动态趋势 {@link Trend}，
     * 统一封装为 {@link EmotionStatisticsResult}。
     *
     * @param validRounds 有效情绪轮次列表（上游 {@link SessionCleanResult} 已保证按轮次号升序排列）
     * @return 完整的情绪统计数据包
     */
    private EmotionStatisticsResult buildStatisticsResult(List<RoundEffectiveLevel> validRounds) {
        CompletableFuture<BaseInfo> baseInfoFuture = CompletableFuture.supplyAsync(() -> buildBaseInfo(validRounds), diagnosisExecutor);
        CompletableFuture<Quantitative> quantitativeFuture = CompletableFuture.supplyAsync(() -> buildQuantitative(validRounds), diagnosisExecutor);
        CompletableFuture<Trend> trendFuture = CompletableFuture.supplyAsync(() -> buildTrend(validRounds), diagnosisExecutor);

        CompletableFuture.allOf(baseInfoFuture, quantitativeFuture, trendFuture).join();

        BaseInfo baseInfo = baseInfoFuture.join();
        Quantitative quantitative = quantitativeFuture.join();
        Trend trend = trendFuture.join();

        return EmotionStatisticsResult.builder()
                .hasValidData(true)
                .validRoundNum(validRounds.size())
                .baseInfo(baseInfo)
                .quantitative(quantitative)
                .trend(trend)
                .build();
    }

    /**
     * 构建基础情绪分布信息
     * <p>
     * 包含三路统计：
     * <ul>
     *   <li><b>主情感标签统计</b>：按 {@code emotionLabel} 分组，计算各标签出现频次、占比、
     *       置信度加权平均强度、峰值强度，按占比降序排列</li>
     *   <li><b>子情感标签统计</b>：按 {@code emotionSubLabel} 分组，计算各标签出现频次与占比，
     *       按占比降序排列</li>
     *   <li><b>正负向情绪分类统计</b>：对正向/中性/负向占比以置信度为权重做加权平均</li>
     * </ul>
     *
     * @param validRounds 有效情绪轮次列表
     * @return 基础情绪分布信息
     * @see BaseInfo
     * @see EmotionDistributionItem
     * @see SubEmotionDistributionItem
     */
    private BaseInfo buildBaseInfo(List<RoundEffectiveLevel> validRounds) {
        int total = validRounds.size();

        Map<String, List<EmotionAnalysis>> byLabel = validRounds.stream()
                .map(RoundEffectiveLevel::getEmotionAnalysis)
                .filter(e -> e.getEmotionLabel() != null)
                .collect(Collectors.groupingBy(EmotionAnalysis::getEmotionLabel));

        List<EmotionDistributionItem> emotionDistribution = byLabel.entrySet().stream()
                .map(entry -> {
                    String label = entry.getKey();
                    List<EmotionAnalysis> analyses = entry.getValue();
                    int count = analyses.size();
                    BigDecimal ratio = bd(count).divide(bd(total), SCALE, RoundingMode.HALF_UP);
                    BigDecimal avgIntensity = weightedAvg(analyses, EmotionAnalysis::getEmotionIntensity, EmotionAnalysis::getEmotionConfidence);
                    BigDecimal peakIntensity = analyses.stream()
                            .map(EmotionAnalysis::getEmotionIntensity)
                            .filter(Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .map(this::bd).orElse(BigDecimal.ZERO);
                    BigDecimal avgConfidence = simpleAvg(analyses, EmotionAnalysis::getEmotionConfidence);
                    return EmotionDistributionItem.builder()
                            .label(label)
                            .count(count)
                            .ratio(ratio)
                            .avgConfidence(avgConfidence)
                            .avgIntensity(avgIntensity)
                            .peakIntensity(peakIntensity)
                            .build();
                })
                .sorted(Comparator.comparing(EmotionDistributionItem::getRatio).reversed())
                .collect(Collectors.toList());

        Map<String, List<EmotionAnalysis>> bySubLabel = validRounds.stream()
                .map(RoundEffectiveLevel::getEmotionAnalysis)
                .filter(e -> e.getEmotionSubLabel() != null)
                .collect(Collectors.groupingBy(EmotionAnalysis::getEmotionSubLabel));

        List<SubEmotionDistributionItem> subEmotionDistribution = bySubLabel.entrySet().stream()
                .map(entry -> {
                    String label = entry.getKey();
                    List<EmotionAnalysis> analyses = entry.getValue();
                    int count = analyses.size();
                    BigDecimal ratio = bd(count).divide(bd(total), SCALE, RoundingMode.HALF_UP);
                    BigDecimal avgConfidence = simpleAvg(analyses, EmotionAnalysis::getEmotionConfidence);
                    return SubEmotionDistributionItem.builder()
                            .label(label)
                            .count(count)
                            .ratio(ratio)
                            .avgConfidence(avgConfidence)
                            .build();
                })
                .sorted(Comparator.comparing(SubEmotionDistributionItem::getRatio).reversed())
                .collect(Collectors.toList());

        BigDecimal avgPositiveRatio = weightedAvg(validRounds,
                r -> r.getEmotionAnalysis().getPositiveEmotionRatio(),
                r -> r.getEmotionAnalysis().getEmotionConfidence());
        BigDecimal avgNeutralRatio = weightedAvg(validRounds,
                r -> r.getEmotionAnalysis().getNeutralEmotionRatio(),
                r -> r.getEmotionAnalysis().getEmotionConfidence());
        BigDecimal avgNegativeRatio = weightedAvg(validRounds,
                r -> r.getEmotionAnalysis().getNegativeEmotionRatio(),
                r -> r.getEmotionAnalysis().getEmotionConfidence());
        BigDecimal avgConfidence = simpleAvg(validRounds,
                r -> r.getEmotionAnalysis().getEmotionConfidence());

        EmotionDistributionItem dominantEmotion = emotionDistribution.isEmpty() ? null : emotionDistribution.get(0);

        return BaseInfo.builder()
                .dominantEmotion(dominantEmotion)
                .emotionDistribution(emotionDistribution)
                .subEmotionDistribution(subEmotionDistribution)
                .avgPositiveRatio(avgPositiveRatio)
                .avgNeutralRatio(avgNeutralRatio)
                .avgNegativeRatio(avgNegativeRatio)
                .avgConfidence(avgConfidence)
                .build();
    }

    /**
     * 构建量化指标统计（PAD三维 + 情绪强度）
     * <p>
     * 包含两路统计：
     * <ul>
     *   <li><b>PAD三维特征统计</b>：分别对P/A/D维度调用 {@link #buildPadDimension} 计算加权均值、
     *       极值、加权标准差、波动区间、最新取值</li>
     *   <li><b>情绪烈度指标统计</b>：单次遍历同时计算加权平均强度、峰值/谷值及对应轮次号、波动幅度</li>
     * </ul>
     *
     * @param validRounds 有效情绪轮次列表
     * @return 量化指标统计结果
     * @see Quantitative
     * @see PadDimension
     * @see IntensityStat
     */
    private Quantitative buildQuantitative(List<RoundEffectiveLevel> validRounds) {
        PadDimension pDim = buildPadDimension(validRounds, EmotionAnalysis::getPScore);
        PadDimension aDim = buildPadDimension(validRounds, EmotionAnalysis::getAScore);
        PadDimension dDim = buildPadDimension(validRounds, EmotionAnalysis::getDScore);

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal valley = INTENSITY_UPPER_BOUND;
        int peakRound = 0;
        int valleyRound = 0;

        for (RoundEffectiveLevel r : validRounds) {
            EmotionAnalysis ea = r.getEmotionAnalysis();
            Double intensity = ea.getEmotionIntensity();
            Double confidence = ea.getEmotionConfidence();
            if (intensity != null) {
                BigDecimal val = bd(intensity);
                if (val.compareTo(peak) > 0) {
                    peak = val;
                    peakRound = r.getRoundNum();
                }
                if (val.compareTo(valley) < 0) {
                    valley = val;
                    valleyRound = r.getRoundNum();
                }
                if (confidence != null) {
                    BigDecimal w = bd(confidence);
                    weightedSum = weightedSum.add(val.multiply(w));
                    weightTotal = weightTotal.add(w);
                }
            }
        }

        BigDecimal avgIntensity = weightTotal.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : weightedSum.divide(weightTotal, SCALE, RoundingMode.HALF_UP);

        BigDecimal waveRange = peak.subtract(valley);

        IntensityStat intensityStat = IntensityStat.builder()
                .avg(avgIntensity)
                .peak(peak)
                .peakRound(peakRound)
                .valley(valley)
                .valleyRound(valleyRound)
                .waveRange(waveRange)
                .build();

        return Quantitative.builder()
                .p(pDim)
                .a(aDim)
                .d(dDim)
                .intensity(intensityStat)
                .build();
    }

    /**
     * 构建PAD单维度统计结果
     * <p>
     * 对指定维度（P/A/D）计算以下指标：
     * <ul>
     *   <li>{@code avg}：置信度加权平均值</li>
     *   <li>{@code max}/{@code min}：维度极值</li>
     *   <li>{@code waveRange}：波动幅度 = max - min</li>
     *   <li>{@code latest}：最新一轮对话的维度数值</li>
     *   <li>{@code std}：置信度加权标准差，与加权均值口径一致</li>
     * </ul>
     *
     * @param validRounds 有效情绪轮次列表
     * @param getter      维度取值函数（如 {@code EmotionAnalysis::getPScore}）
     * @return PAD单维度统计结果
     * @see PadDimension
     */
    private PadDimension buildPadDimension(List<RoundEffectiveLevel> validRounds,
                                            Function<EmotionAnalysis, Double> getter) {
        List<BigDecimal> values = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();

        for (RoundEffectiveLevel r : validRounds) {
            Double raw = getter.apply(r.getEmotionAnalysis());
            Double confidence = r.getEmotionAnalysis().getEmotionConfidence();
            if (raw != null && confidence != null) {
                values.add(bd(raw));
                weights.add(bd(confidence));
            }
        }

        if (values.isEmpty()) {
            return PadDimension.builder()
                    .avg(BigDecimal.ZERO).max(BigDecimal.ZERO).min(BigDecimal.ZERO)
                    .std(BigDecimal.ZERO).waveRange(BigDecimal.ZERO).latest(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal avg = weightedAvgDirect(values, weights);
        BigDecimal max = Collections.max(values);
        BigDecimal min = Collections.min(values);
        BigDecimal waveRange = max.subtract(min);
        BigDecimal latest = values.get(values.size() - 1);
        BigDecimal std = calcWeightedStd(values, weights, avg);

        return PadDimension.builder()
                .avg(avg)
                .max(max)
                .min(min)
                .std(std)
                .waveRange(waveRange)
                .latest(latest)
                .build();
    }

    /**
     * 构建情绪动态趋势特征
     * <p>
     * 计算以下趋势指标：
     * <ul>
     *   <li>{@code emotionTrend}：基于P维度（愉悦度）做二分法趋势判断，
     *       前后半段均值差 &gt; 0.1 为上升，&lt; -0.1 为下降，区间内为平稳</li>
     *   <li>{@code emotionStableRounds}：相邻两轮强度差值绝对值 &lt; 0.1 的相邻对数
     *       （n轮对应n-1个相邻对，非平稳轮次数量）</li>
     *   <li>{@code emotionStabilityScore}：基于加权标准差做归一化得分：1 - 加权标准差，
     *       值域 0~1，得分越高情绪越稳定</li>
     *   <li>{@code emotionChangeCount}：相邻轮次主情绪标签变化的总次数</li>
     *   <li>{@code negativeChangeCount}：相邻轮次P维度下降幅度 &gt; 0.1 的次数，即情绪负向恶化的次数</li>
     * </ul>
     *
     * @param validRounds   有效情绪轮次列表
     * @return 情绪动态趋势特征
     * @see Trend
     */
    private Trend buildTrend(List<RoundEffectiveLevel> validRounds) {
        List<BigDecimal> pValues = validRounds.stream()
                .map(RoundEffectiveLevel::getEmotionAnalysis)
                .map(EmotionAnalysis::getPScore)
                .filter(Objects::nonNull)
                .map(this::bd)
                .collect(Collectors.toList());

        String emotionTrend = EmotionStatisticsResult.TREND_UNKNOWN;
        if (pValues.size() >= 2) {
            int mid = pValues.size() / 2;
            BigDecimal firstHalfAvg = avgOfList(pValues.subList(0, mid));
            BigDecimal secondHalfAvg = avgOfList(pValues.subList(mid, pValues.size()));
            BigDecimal diff = secondHalfAvg.subtract(firstHalfAvg);
            if (diff.compareTo(TREND_THRESHOLD) > 0) {
                emotionTrend = EmotionStatisticsResult.TREND_RISING;
            } else if (diff.compareTo(TREND_THRESHOLD.negate()) < 0) {
                emotionTrend = EmotionStatisticsResult.TREND_FALLING;
            } else {
                emotionTrend = EmotionStatisticsResult.TREND_STABLE;
            }
        }

        List<BigDecimal> intensities = new ArrayList<>();
        List<BigDecimal> intensityWeights = new ArrayList<>();
        for (RoundEffectiveLevel r : validRounds) {
            EmotionAnalysis ea = r.getEmotionAnalysis();
            if (ea.getEmotionIntensity() != null && ea.getEmotionConfidence() != null) {
                intensities.add(bd(ea.getEmotionIntensity()));
                intensityWeights.add(bd(ea.getEmotionConfidence()));
            }
        }

        int emotionStableRounds = 0;
        for (int i = 1; i < intensities.size(); i++) {
            BigDecimal diff = intensities.get(i).subtract(intensities.get(i - 1)).abs();
            if (diff.compareTo(STABLE_DIFF_THRESHOLD) < 0) {
                emotionStableRounds++;
            }
        }

        BigDecimal intensityWeightedAvg = intensities.isEmpty()
                ? BigDecimal.ZERO
                : weightedAvgDirect(intensities, intensityWeights);
        BigDecimal intensityStd = calcWeightedStd(intensities, intensityWeights, intensityWeightedAvg);
        BigDecimal emotionStabilityScore = BigDecimal.ONE.subtract(intensityStd);
        if (emotionStabilityScore.compareTo(BigDecimal.ZERO) < 0) {
            emotionStabilityScore = BigDecimal.ZERO;
        }

        int emotionChangeCount = 0;
        for (int i = 1; i < validRounds.size(); i++) {
            String prev = validRounds.get(i - 1).getEmotionAnalysis().getEmotionLabel();
            String curr = validRounds.get(i).getEmotionAnalysis().getEmotionLabel();
            if (prev != null && curr != null && !prev.equals(curr)) {
                emotionChangeCount++;
            }
        }

        int negativeChangeCount = 0;
        for (int i = 1; i < validRounds.size(); i++) {
            Double prevP = validRounds.get(i - 1).getEmotionAnalysis().getPScore();
            Double currP = validRounds.get(i).getEmotionAnalysis().getPScore();
            if (prevP != null && currP != null) {
                BigDecimal diff = bd(currP).subtract(bd(prevP));
                if (diff.compareTo(TREND_THRESHOLD.negate()) < 0) {
                    negativeChangeCount++;
                }
            }
        }

        return Trend.builder()
                .emotionTrend(emotionTrend)
                .emotionStableRounds(emotionStableRounds)
                .emotionStabilityScore(emotionStabilityScore)
                .emotionChangeCount(emotionChangeCount)
                .negativeChangeCount(negativeChangeCount)
                .build();
    }

    /**
     * 置信度加权平均
     * <p>
     * 计算公式：{@code sum(value * weight) / sum(weight)}
     * <br>当权重总和为0时返回 {@link BigDecimal#ZERO}。
     *
     * @param items       待计算的数据项列表
     * @param valueGetter 数值提取函数
     * @param weightGetter 权重提取函数（通常为置信度）
     * @param <T>         数据项类型
     * @return 加权平均值，保留 {@value #SCALE} 位小数
     */
    private <T> BigDecimal weightedAvg(List<T> items,
                                        Function<T, Double> valueGetter,
                                        Function<T, Double> weightGetter) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (T item : items) {
            Double val = valueGetter.apply(item);
            Double weight = weightGetter.apply(item);
            if (val != null && weight != null) {
                BigDecimal v = bd(val);
                BigDecimal w = bd(weight);
                weightedSum = weightedSum.add(v.multiply(w));
                weightTotal = weightTotal.add(w);
            }
        }
        if (weightTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return weightedSum.divide(weightTotal, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 基于预提取的数值列表和权重列表直接计算加权平均
     * <p>
     * 计算公式：{@code sum(value[i] * weight[i]) / sum(weight[i])}
     * <br>当权重总和为0时返回 {@link BigDecimal#ZERO}。
     *
     * @param values  数值列表
     * @param weights 权重列表（与 values 等长）
     * @return 加权平均值，保留 {@value #SCALE} 位小数
     */
    private BigDecimal weightedAvgDirect(List<BigDecimal> values, List<BigDecimal> weights) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (int i = 0; i < values.size(); i++) {
            BigDecimal v = values.get(i);
            BigDecimal w = weights.get(i);
            weightedSum = weightedSum.add(v.multiply(w));
            weightTotal = weightTotal.add(w);
        }
        if (weightTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return weightedSum.divide(weightTotal, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 简单算术平均
     * <p>
     * 计算公式：{@code sum(value) / count}
     * <br>当有效数据项为0时返回 {@link BigDecimal#ZERO}。
     *
     * @param items       待计算的数据项列表
     * @param valueGetter 数值提取函数
     * @param <T>         数据项类型
     * @return 算术平均值，保留 {@value #SCALE} 位小数
     */
    private <T> BigDecimal simpleAvg(List<T> items,
                                      Function<T, Double> valueGetter) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (T item : items) {
            Double val = valueGetter.apply(item);
            if (val != null) {
                sum = sum.add(bd(val));
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(bd(count), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 对 BigDecimal 列表求算术平均
     *
     * @param values 数值列表
     * @return 算术平均值，空列表返回 {@link BigDecimal#ZERO}
     */
    private BigDecimal avgOfList(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(bd(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算置信度加权标准差
     * <p>
     * 计算公式：{@code sqrt(sum(weight[i] * (x[i] - avg)^2) / sum(weight[i]))}
     * <br>与 {@link #weightedAvg} 口径一致，确保均值与标准差的权重体系匹配。
     * <br>当数据量 ≤ 1 或权重总和为0时返回 {@link BigDecimal#ZERO}。
     *
     * @param values  数值列表
     * @param weights 权重列表（与 values 等长）
     * @param avg     已计算的加权平均值
     * @return 加权标准差，保留 {@value #SCALE} 位小数
     */
    private BigDecimal calcWeightedStd(List<BigDecimal> values, List<BigDecimal> weights, BigDecimal avg) {
        if (values.size() <= 1) {
            return BigDecimal.ZERO;
        }
        BigDecimal weightedSumSquares = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (int i = 0; i < values.size(); i++) {
            BigDecimal diff = values.get(i).subtract(avg);
            BigDecimal w = weights.get(i);
            weightedSumSquares = weightedSumSquares.add(diff.multiply(diff).multiply(w));
            weightTotal = weightTotal.add(w);
        }
        if (weightTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal variance = weightedSumSquares.divide(weightTotal, SCALE, RoundingMode.HALF_UP);
        return sqrt(variance);
    }

    /**
     * BigDecimal 高精度开平方（牛顿迭代法）
     * <p>
     * 避免通过 {@code Math.sqrt(doubleValue())} 丢失精度，
     * 使用牛顿迭代法在 {@link BigDecimal} 域内直接求解，迭代至 {@value #SCALE} + 1 位精度收敛。
     *
     * @param value 非负数值
     * @return 平方根，保留 {@value #SCALE} 位小数
     */
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal x0 = BigDecimal.ZERO;
        BigDecimal x1 = new BigDecimal(Math.sqrt(value.doubleValue()));
        MathContext mc = new MathContext(SCALE + 1, RoundingMode.HALF_UP);
        BigDecimal two = BigDecimal.valueOf(2);
        while (x1.subtract(x0).abs().compareTo(BigDecimal.ONE.movePointLeft(SCALE + 1)) > 0) {
            x0 = x1;
            x1 = x0.add(value.divide(x0, mc)).divide(two, mc);
        }
        return x1.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Number → BigDecimal 安全转换
     * <p>
     * 通过 {@code toString()} 构造，避免 Double 直接构造的精度丢失问题。
     *
     * @param num 数值
     * @return 等价的 BigDecimal
     */
    private BigDecimal bd(Number num) {
        return new BigDecimal(num.toString());
    }
}