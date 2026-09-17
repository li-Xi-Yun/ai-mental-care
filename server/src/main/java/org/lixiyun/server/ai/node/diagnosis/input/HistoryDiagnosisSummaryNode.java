package org.lixiyun.server.ai.node.diagnosis.input;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.*;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionBasicInfo;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionDimensionTrend;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionPADStat;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionRatioStat;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 输入侧-历史诊断摘要聚合节点
 * <p>
 * 将历史诊断结果集合进行多维度分析，生成结构化摘要，供后续诊断流程参考。
 * <p>
 * 处理流程：
 * <ol>
 *   <li><b>数据集判空校验</b>：历史诊断结果集合为空时直接返回首次诊断空摘要，流程结束</li>
 *   <li><b>多维度分析</b>：依次执行五个维度的数据梳理工作
 *     <ul>
 *       <li>症状演变维度：梳理历次诊断中用户症状的发展、变化情况</li>
 *       <li>情绪变化维度：汇总不同会话周期内用户情绪波动与演变特征</li>
 *       <li>诊断结论维度：整理历史全部诊断结论，对比结论延续、变更情况</li>
 *       <li>干预方案维度：汇总过往使用的干预手段、方案内容及执行情况</li>
 *       <li>关键风险点维度：提取历次识别出的风险信息，跟踪风险变化趋势</li>
 *     </ul>
 *   </li>
 *   <li><b>结构化摘要封装</b>：汇总以上五个维度的分析结果，统一完成结构化摘要封装</li>
 * </ol>
 *
 * @author lixiyun
 * @since 2026-08-11 10:24
 * @see HistoryDiagnosisSummaryResult
 * @see SymptomEvolution
 * @see EmotionTrend
 * @see DiagnosisSummary
 * @see InterventionHistory
 * @see RiskPoints
 */
@Slf4j
@Component
public class HistoryDiagnosisSummaryNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "history-diagnosis-summary-node";

    @Autowired
    private EmotionDiagnosisMapper emotionDiagnosisMapper;

    @Autowired
    @Qualifier("diagnosisThreadPoolTaskExecutor")
    private ThreadPoolTaskExecutor diagnosisExecutor;

    private static final int MAX_HISTORY_COUNT = 10;
    private static final String firstDiagnosisPrompt = "用户首次诊断，无历史记录";

    private static final double PERSISTENT_THRESHOLD = 0.7;
    private static final int OBSERVATION_PERIOD_SIZE = 3;

    /**
     * 执行历史诊断摘要聚合流程
     * <p>
     * 从全局状态中获取输入侧结果与会话上下文，按会话ID与用户ID查询最近 {@code MAX_HISTORY_COUNT}（10）条
     * 历史诊断记录（按创建时间降序），依次执行判空校验、多维度分析、结构化摘要封装，
     * 再通过独立查询获取历史诊断总数并写入结果，最终将 {@link HistoryDiagnosisSummaryResult} 写回 {@link InputResult}。
     *
     * @param state  全局状态，包含 {@link InputResult} 与 {@link ConversationProcessContextBO}
     * @param config 运行配置
     * @return 空Map（结果通过 state 中的 {@link InputResult} 传递）
     * @throws BusinessException 当输入结果或会话上下文为空时抛出
     */
    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("输入侧-历史诊断摘要聚合-开始");

        Optional<InputResult> inputResultOpt = state.value(InputResult.NAME);
        if (inputResultOpt.isEmpty()) {
            log.error("输入侧-历史诊断摘要聚合-输入侧流程聚合结果为空");
            throw new BusinessException(ConversationExceptionEnum.INPUT_RESULT_NOT_EXIST);
        }
        InputResult inputResult = inputResultOpt.get();

        Optional<ConversationProcessContextBO> contextBOOpt = state.value(ConversationProcessContextBO.NAME);
        if (contextBOOpt.isEmpty()) {
            log.error("输入侧-历史诊断摘要聚合-会话上下文为空");
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_PROCESS_CONTEXT_NOT_EXIST);
        }
        ConversationProcessContextBO contextBO = contextBOOpt.get();

        List<EmotionDiagnosis> emotionDiagnosisList = emotionDiagnosisMapper.selectList(
                new LambdaQueryWrapper<EmotionDiagnosis>()
                        .eq(EmotionDiagnosis::getConversationId, contextBO.getConversation().getId())
                        .eq(EmotionDiagnosis::getUserId, contextBO.getConversation().getUserId())
                        .orderByDesc(EmotionDiagnosis::getCreatedTime)
                        .last("limit " + MAX_HISTORY_COUNT)
        );
        log.debug("输入侧-历史诊断摘要聚合-查询到最近{}条诊断记录", emotionDiagnosisList.size());

        HistoryDiagnosisSummaryResult result = checkAndBuildSummary(emotionDiagnosisList);

        long count = emotionDiagnosisMapper.selectCount(
                new LambdaQueryWrapper<EmotionDiagnosis>()
                        .eq(EmotionDiagnosis::getConversationId, contextBO.getConversation().getId())
                        .eq(EmotionDiagnosis::getUserId, contextBO.getConversation().getUserId())
        );
        result.setHistoryCount((int) count);

        inputResult.setHistoryDiagnosisSummaryResult(result);
        log.info("输入侧-历史诊断摘要聚合-完成");
        return Map.of();
    }

    /**
     * 步骤1：数据集判空校验
     * <p>
     * 若历史诊断结果集合为空，返回标记为首次诊断的空摘要（含首次诊断提示词），本方法流程结束；
     * 若集合存在有效历史诊断数据，调用 {@link #parallelAnalysis} 依次执行五个维度的分析。
     *
     * @param emotionDiagnosisList 历史诊断结果集合（按创建时间降序）
     * @return 历史诊断摘要聚合结果
     */
    private HistoryDiagnosisSummaryResult checkAndBuildSummary(List<EmotionDiagnosis> emotionDiagnosisList) {
        if (emotionDiagnosisList.isEmpty()) {
            log.info("输入侧-历史诊断摘要聚合-无历史诊断记录，返回首次诊断空摘要格式化提示词");
            return HistoryDiagnosisSummaryResult.builder()
                    .isFirstDiagnosis(true)
                    .firstDiagnosisPrompt(firstDiagnosisPrompt)
                    .build();
        }
        return parallelAnalysis(emotionDiagnosisList);
    }

    /**
     * 步骤2：多维度分析
     * <p>
     * 依次执行五个维度的数据梳理工作，各维度独立计算后返回对应结果：
     * <ul>
     *   <li>{@link #buildSymptomEvolution}：症状演变维度</li>
     *   <li>{@link #buildEmotionTrend}：情绪变化维度</li>
     *   <li>{@link #buildDiagnosisSummary}：诊断结论维度</li>
     *   <li>{@link #buildInterventionHistory}：干预方案维度</li>
     *   <li>{@link #buildRiskPointsList}：关键风险点维度</li>
     * </ul>
     * 同时设置 isFirstDiagnosis=false、historyCount 为集合大小、
     * lastDiagnosisTime 为列表末尾（即按创建时间降序排列中最早一次）诊断记录的创建时间。
     *
     * @param emotionDiagnosisList 历史诊断结果集合（按创建时间降序）
     * @return 五维度分析结果的聚合记录 {@link HistoryDiagnosisSummaryResult}
     */
    private HistoryDiagnosisSummaryResult parallelAnalysis(List<EmotionDiagnosis> emotionDiagnosisList) {
        CompletableFuture<SymptomEvolution> symptomEvolutionFuture = CompletableFuture.supplyAsync(() -> buildSymptomEvolution(emotionDiagnosisList), diagnosisExecutor);
        CompletableFuture<EmotionTrend> emotionTrendFuture = CompletableFuture.supplyAsync(() -> buildEmotionTrend(emotionDiagnosisList), diagnosisExecutor);
        CompletableFuture<DiagnosisSummary> diagnosisSummaryFuture = CompletableFuture.supplyAsync(() -> buildDiagnosisSummary(emotionDiagnosisList), diagnosisExecutor);
        CompletableFuture<InterventionHistory> interventionHistoryFuture = CompletableFuture.supplyAsync(() -> buildInterventionHistory(emotionDiagnosisList), diagnosisExecutor);
        CompletableFuture<List<RiskPoints>> riskPointsListFuture = CompletableFuture.supplyAsync(() -> buildRiskPointsList(emotionDiagnosisList), diagnosisExecutor);

        CompletableFuture.allOf(symptomEvolutionFuture, emotionTrendFuture, diagnosisSummaryFuture, interventionHistoryFuture, riskPointsListFuture).join();

        SymptomEvolution symptomEvolution = symptomEvolutionFuture.join();
        EmotionTrend emotionTrend = emotionTrendFuture.join();
        DiagnosisSummary diagnosisSummary = diagnosisSummaryFuture.join();
        InterventionHistory interventionHistory = interventionHistoryFuture.join();
        List<RiskPoints> riskPointsList = riskPointsListFuture.join();

        return HistoryDiagnosisSummaryResult.builder()
                .isFirstDiagnosis(false)
                .lastDiagnosisTime(emotionDiagnosisList.get(0).getCreatedTime())
                .symptomEvolution(symptomEvolution)
                .emotionTrend(emotionTrend)
                .diagnosisSummary(diagnosisSummary)
                .interventionHistory(interventionHistory)
                .riskPointsList(riskPointsList)
                .build();
    }

    /**
     * 步骤2.1：症状演变维度
     * <p>
     * 梳理历次诊断中用户症状的发展、变化情况，包括：
     * <ul>
     *   <li>持续症状：全局出现次数 ≥ ceil(总诊断数×{@value #PERSISTENT_THRESHOLD}) 的症状，value=全局历史总出现次数</li>
     *   <li>新增症状：观察期存在但基准期从未出现的症状，value=观察期内出现次数</li>
     *   <li>缓解症状：基准期内持续(出现次数 ≥ ceil(基准期数×{@value #PERSISTENT_THRESHOLD}))且观察期消失的症状，value=基准期内出现次数</li>
     * </ul>
     * <p>
     * 观察期/基准期划分规则（集合按创建时间降序，前段为观察期、后段为基准期）：
     * <ul>
     *   <li>总诊断数为1时，仅返回持续症状，新增与缓解症状为空</li>
     *   <li>总诊断数达到 {@code MAX_HISTORY_COUNT}（10）时，观察期大小为 {@value #OBSERVATION_PERIOD_SIZE}</li>
     *   <li>其余情况观察期大小为1，基准期大小为总诊断数减观察期大小</li>
     * </ul>
     *
     * @param emotionDiagnosisList 历史诊断结果集合（按创建时间降序）
     * @return 症状演变维度分析结果
     * @see SymptomEvolution
     */
    private SymptomEvolution buildSymptomEvolution(List<EmotionDiagnosis> emotionDiagnosisList) {
        int totalSize = emotionDiagnosisList.size();

        Map<String, Integer> globalSymptomCount = new LinkedHashMap<>();
        for (EmotionDiagnosis diagnosis : emotionDiagnosisList) {
            List<String> tags = splitSymptomTags(diagnosis.getSymptomTags());
            for (String tag : tags) {
                globalSymptomCount.merge(tag, 1, Integer::sum);
            }
        }
        globalSymptomCount = sortByValueDescending(globalSymptomCount);

        int persistentThreshold = (int) Math.ceil(totalSize * PERSISTENT_THRESHOLD);
        Map<String, Integer> persistentSymptomMap = globalSymptomCount.entrySet().stream()
                .filter(e -> e.getValue() >= persistentThreshold)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        if (totalSize == 1) {
            return SymptomEvolution.builder()
                    .persistentSymptomMap(persistentSymptomMap)
                    .newSymptomMap(Collections.emptyMap())
                    .relievedSymptomMap(Collections.emptyMap())
                    .build();
        }

        int observationSize;
        int baselineSize;
        if(totalSize == MAX_HISTORY_COUNT){
            observationSize = OBSERVATION_PERIOD_SIZE;
        } else{
            observationSize = 1;
        }
        baselineSize = totalSize - observationSize;

        List<EmotionDiagnosis> observationList = emotionDiagnosisList.subList(0, observationSize);
        List<EmotionDiagnosis> baselineList = emotionDiagnosisList.subList(observationSize, totalSize);

        Set<String> baselineSymptomSet = extractSymptomSet(baselineList);
        Map<String, Integer> baselineSymptomCount = countSymptoms(baselineList);
        Map<String, Integer> observationSymptomCount = countSymptoms(observationList);

        Map<String, Integer> newSymptomMap = observationSymptomCount.entrySet().stream()
                .filter(e -> !baselineSymptomSet.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        int baselinePersistentThreshold = (int) Math.ceil(baselineSize * PERSISTENT_THRESHOLD);
        Map<String, Integer> relievedSymptomMap = baselineSymptomCount.entrySet().stream()
                .filter(e -> e.getValue() >= baselinePersistentThreshold)
                .filter(e -> observationSymptomCount.getOrDefault(e.getKey(), 0) == 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        return SymptomEvolution.builder()
                .persistentSymptomMap(persistentSymptomMap)
                .newSymptomMap(newSymptomMap)
                .relievedSymptomMap(relievedSymptomMap)
                .build();
    }

    /**
     * 步骤2.2：情绪变化维度
     * <p>
     * 先将历史诊断集合按时间反转为升序，再汇总不同会话周期内用户情绪波动与演变特征，包括：
     * <ul>
     *   <li>历史主导情绪标签与高频情绪Top3</li>
     *   <li>整体情绪趋势（逐步好转/持续加重/波动反复/无法判断）</li>
     *   <li>情绪稳定性平均得分</li>
     *   <li>PAD三维历史均值与标准差</li>
     *   <li>正负向情绪占比统计</li>
     *   <li>愉悦度/唤醒度/支配度变化趋势</li>
     * </ul>
     *
     * @param emotionDiagnosisList 历史诊断结果集合（按创建时间降序）
     * @return 情绪变化维度分析结果
     * @see EmotionTrend
     */
    private EmotionTrend buildEmotionTrend(List<EmotionDiagnosis> emotionDiagnosisList) {
        List<EmotionDiagnosis> ascList = new ArrayList<>(emotionDiagnosisList);
        Collections.reverse(ascList);

        EmotionBasicInfo basicInfo = buildEmotionBasicInfo(ascList);
        EmotionPADStat padStat = buildEmotionPADStat(ascList);
        EmotionDimensionTrend dimensionTrend = buildEmotionDimensionTrend(ascList);
        EmotionRatioStat ratioStat = buildEmotionRatioStat(ascList);

        return EmotionTrend.builder()
                .basicInfo(basicInfo)
                .padStat(padStat)
                .dimensionTrend(dimensionTrend)
                .ratioStat(ratioStat)
                .build();
    }

    /**
     * 步骤2.2.1：基础情绪概况
     * <p>
     * 统计历史主导情绪、高频情绪Top3、情绪丰富度、整体情绪趋势、
     * 平均稳定性得分及情绪最差节点描述。
     * <ul>
     *   <li>情绪计数权重：核心情绪权重为2，次级情绪权重为1，按计数降序排列</li>
     *   <li>历史主导情绪：计数最高的情绪标签</li>
     *   <li>情绪最差节点：取愉悦度(avgP)最低的诊断记录，格式化为含轮次、时间、愉悦度与核心情绪的描述</li>
     *   <li>整体情绪趋势：基于愉悦度(avgP)通过 {@link #computeTrend} 判定</li>
     * </ul>
     *
     * @param ascList 按时间升序排列的历史诊断集合
     * @return 基础情绪概况
     * @see EmotionBasicInfo
     */
    private EmotionBasicInfo buildEmotionBasicInfo(List<EmotionDiagnosis> ascList) {
        int totalSize = ascList.size();

        // 统计历史主导情绪
        Map<String, Integer> emotionCountMap = new LinkedHashMap<>();
        EmotionDiagnosis worstDiagnosis = ascList.get(0);
        for (EmotionDiagnosis diagnosis : ascList) {
            String coreLabel = diagnosis.getCoreEmotionLabel();
            if (coreLabel != null && !coreLabel.isBlank()) {
                emotionCountMap.merge(coreLabel.trim(), 2, Integer::sum);
            }
            Map<String, BigDecimal> secondary = diagnosis.getSecondaryEmotion();
            if (secondary != null) {
                for (String label : secondary.keySet()) {
                    if (label != null && !label.isBlank()) {
                        emotionCountMap.merge(label.trim(), 1, Integer::sum);
                    }
                }
            }
            // 更新最差节点
            if (toDouble(worstDiagnosis.getAvgP()) > toDouble(diagnosis.getAvgP())) {
                worstDiagnosis = diagnosis;
            }
        }
        emotionCountMap = sortByValueDescending(emotionCountMap);

        // 获取 LinkedHashMap 里第一个插入元素的键，作为历史主导情绪
        String dominantEmotion = emotionCountMap.isEmpty() ? null : emotionCountMap.entrySet().iterator().next().getKey();
        List<String> allEmotionLabels = new ArrayList<>(emotionCountMap.keySet());
        List<String> topEmotionList = emotionCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        String overallTrend = computeTrend(ascList, totalSize, EmotionDiagnosis::getAvgP);

        BigDecimal avgStabilityScore = BigDecimal.valueOf(
                ascList.stream()
                        .mapToDouble(d -> toDouble(d.getEmotionStabilityScore()))
                        .average()
                        .orElse(0.0));

        String worstEmotionNode;
        int roundNum = worstDiagnosis.getRoundNum() != null ? worstDiagnosis.getRoundNum() : 0;
        String timeStr = worstDiagnosis.getCreatedTime() != null ? worstDiagnosis.getCreatedTime().toString() : "未知时间";
        double pVal = toDouble(worstDiagnosis.getAvgP());
        String coreLabel = worstDiagnosis.getCoreEmotionLabel() != null ? worstDiagnosis.getCoreEmotionLabel() : "未知";
        worstEmotionNode = "第" + roundNum + "次诊断（" + timeStr + "），愉悦度" + String.format("%.2f", pVal) + "，核心情绪：" + coreLabel;

        return EmotionBasicInfo.builder()
                .dominantEmotion(dominantEmotion)
                .overallTrend(overallTrend)
                .avgStabilityScore(avgStabilityScore)
                .worstEmotionNode(worstEmotionNode)
                .allEmotionLabels(allEmotionLabels)
                .topEmotionList(topEmotionList)
                .build();
    }

    /**
     * 步骤2.2.2：PAD三维度统计
     * <p>
     * 计算P/A/D三维度的历史均值与总体标准差。
     *
     * @param ascList 按时间升序排列的历史诊断集合
     * @return PAD三维统计数据
     * @see EmotionPADStat
     */
    private EmotionPADStat buildEmotionPADStat(List<EmotionDiagnosis> ascList) {
        double avgPDouble = ascList.stream()
                .mapToDouble(d -> toDouble(d.getAvgP()))
                .average()
                .orElse(0.0);
        double avgADouble = ascList.stream()
                .mapToDouble(d -> toDouble(d.getAvgA()))
                .average()
                .orElse(0.0);
        double avgDDouble = ascList.stream()
                .mapToDouble(d -> toDouble(d.getAvgD()))
                .average()
                .orElse(0.0);

        return EmotionPADStat.builder()
                .avgP(BigDecimal.valueOf(avgPDouble))
                .avgA(BigDecimal.valueOf(avgADouble))
                .avgD(BigDecimal.valueOf(avgDDouble))
                .stdP(computeStd(ascList, avgPDouble, EmotionDiagnosis::getAvgP))
                .stdA(computeStd(ascList, avgADouble, EmotionDiagnosis::getAvgA))
                .stdD(computeStd(ascList, avgDDouble, EmotionDiagnosis::getAvgD))
                .build();
    }

    /**
     * 步骤2.2.3：情绪维度变化趋势
     * <p>
     * 基于三分段二分法分别判定愉悦度(P)、唤醒度(A)、支配度(D)三个维度的变化趋势。
     *
     * @param ascList 按时间升序排列的历史诊断集合
     * @return 情绪维度变化趋势
     * @see EmotionDimensionTrend
     */
    private EmotionDimensionTrend buildEmotionDimensionTrend(List<EmotionDiagnosis> ascList) {
        int totalSize = ascList.size();
        return EmotionDimensionTrend.builder()
                .pleasureTrend(computeTrend(ascList, totalSize, EmotionDiagnosis::getAvgP))
                .arousalTrend(computeTrend(ascList, totalSize, EmotionDiagnosis::getAvgA))
                .dominanceTrend(computeTrend(ascList, totalSize, EmotionDiagnosis::getAvgD))
                .build();
    }

    /**
     * 步骤2.2.4：情绪占比统计
     * <p>
     * 计算历史平均负向/正向/中性情绪占比，以及高频负向细分情绪Top3。
     * 高频负向细分情绪按各诊断记录中 negativeEmotionDetail 的标签出现次数统计。
     *
     * @param ascList 按时间升序排列的历史诊断集合
     * @return 情绪占比统计信息
     * @see EmotionRatioStat
     */
    private EmotionRatioStat buildEmotionRatioStat(List<EmotionDiagnosis> ascList) {
        BigDecimal avgNegativeRatio = BigDecimal.valueOf(
                ascList.stream()
                        .mapToDouble(d -> toDouble(d.getNegativeEmotionRatio()))
                        .average()
                        .orElse(0.0)
        );
        BigDecimal avgPositiveRatio = BigDecimal.valueOf(
                ascList.stream()
                        .mapToDouble(d -> toDouble(d.getPositiveEmotionRatio()))
                        .average()
                        .orElse(0.0)
        );
        BigDecimal avgNeutralRatio = BigDecimal.valueOf(
                ascList.stream()
                        .mapToDouble(d -> toDouble(d.getNeutralEmotionRatio()))
                        .average()
                        .orElse(0.0)
        );

        Map<String, Integer> negativeCountMap = new LinkedHashMap<>();
        for (EmotionDiagnosis diagnosis : ascList) {
            Map<String, BigDecimal> detail = diagnosis.getNegativeEmotionDetail();
            if (detail != null) {
                for (String label : detail.keySet()) {
                    if (label != null && !label.isBlank()) {
                        negativeCountMap.merge(label.trim(), 1, Integer::sum);
                    }
                }
            }
        }
        List<String> topNegativeEmotions = negativeCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return EmotionRatioStat.builder()
                .avgNegativeRatio(avgNegativeRatio)
                .avgPositiveRatio(avgPositiveRatio)
                .avgNeutralRatio(avgNeutralRatio)
                .topNegativeEmotions(topNegativeEmotions)
                .build();
    }

    /**
     * 三分段二分法趋势判定
     * <p>
     * 将按时间升序的诊断列表分为前30%（基线期）和后30%（观察期），
     * 比较两段均值差值（观察期均值 - 基线期均值），按 {@link EmotionTrend#TREND_THRESHOLD} 阈值判定趋势方向。
     * 历史诊断数 &lt; 3 时返回「无法判断」。
     * <ul>
     *   <li>差值 &gt; 阈值：上升趋势（{@link EmotionTrend#TREND_UP}）</li>
     *   <li>差值 &lt; -阈值：下降趋势（{@link EmotionTrend#TREND_DOWN}）</li>
     *   <li>否则：波动趋势（{@link EmotionTrend#TREND_FLAT}）</li>
     * </ul>
     *
     * @param ascList   按时间升序排列的历史诊断集合
     * @param totalSize 总诊断数
     * @param extractor 维度值提取器
     * @return 趋势枚举描述（逐步好转/持续加重/波动反复/无法判断）
     */
    private String computeTrend(List<EmotionDiagnosis> ascList, int totalSize, Function<EmotionDiagnosis, BigDecimal> extractor) {
        if (totalSize < 3) {
            return EmotionTrend.TREND_UNKNOWN;
        }
        int splitSize = Math.max(1, (int) (totalSize * 0.3));
        double baselineAvg = ascList.stream()
                .limit(splitSize)
                .mapToDouble(d -> toDouble(extractor.apply(d)))
                .average()
                .orElse(0.0);
        double observationAvg = ascList.stream()
                .skip(totalSize - splitSize)
                .mapToDouble(d -> toDouble(extractor.apply(d)))
                .average()
                .orElse(0.0);
        double diff = observationAvg - baselineAvg;
        if (diff > EmotionTrend.TREND_THRESHOLD) {
            return EmotionTrend.TREND_UP;
        } else if (diff < -EmotionTrend.TREND_THRESHOLD) {
            return EmotionTrend.TREND_DOWN;
        } else {
            return EmotionTrend.TREND_FLAT;
        }
    }

    /**
     * 将 {@link BigDecimal} 安全转换为 double
     * <p>
     * 当入参为 null 时返回 0，避免 NPE。
     *
     * @param val 待转换的 BigDecimal 值，可为 null
     * @return 转换后的 double 值，null 返回 0
     */
    private static double toDouble(BigDecimal val) {
        return val == null ? 0 : val.doubleValue();
    }

    /**
     * 总体标准差计算
     * <p>
     * 诊断数 ≤ 1 时返回null（无法判断）。
     *
     * @param ascList   按时间升序排列的历史诊断集合
     * @param avg       该维度历史均值
     * @param extractor 维度值提取器
     * @return 总体标准差，诊断数 ≤ 1 时为null
     */
    private BigDecimal computeStd(List<EmotionDiagnosis> ascList, double avg, Function<EmotionDiagnosis, BigDecimal> extractor) {
        if (ascList.size() <= 1) {
            return null;
        }
        double variance = ascList.stream()
                .mapToDouble(d -> Math.pow(toDouble(extractor.apply(d)) - avg, 2))
                .average()
                .orElse(0.0);
        return BigDecimal.valueOf(Math.sqrt(variance));
    }

    /**
     * 步骤2.3：诊断结论维度
     * <p>
     * 整理历史全部诊断结论，对比结论延续、变更情况，包括：
     * <ul>
     *   <li>历史最高情绪/自伤/自杀风险等级及对应时间（使用 {@code >=} 比较，等级相同时取时间较旧者）</li>
     *   <li>心理状态变化序列：按时间从旧到新拼接，以" → "分隔</li>
     *   <li>历史危机预警次数：统计 crisisWarning 为 YES 的记录数</li>
     *   <li>是否有过人工干预记录：任一记录 needManualIntervene 为 YES 即为 true</li>
     * </ul>
     *
     * @param emotionDiagnosisList 历史诊断结果集合（按创建时间降序）
     * @return 诊断结论维度分析结果
     * @see DiagnosisSummary
     */
    private DiagnosisSummary buildDiagnosisSummary(List<EmotionDiagnosis> emotionDiagnosisList) {
        int highestEmotionRisk = -1;
        LocalDateTime highestEmotionRiskTime = null;
        int highestSelfHarmRisk = -1;
        LocalDateTime highestSelfHarmRiskTime = null;
        int highestSuicideRisk = -1;
        LocalDateTime highestSuicideRiskTime = null;

        for (EmotionDiagnosis diagnosis : emotionDiagnosisList) {
            if (diagnosis.getEmotionRiskLevel() != null && diagnosis.getEmotionRiskLevel() >= highestEmotionRisk) {
                highestEmotionRisk = diagnosis.getEmotionRiskLevel();
                highestEmotionRiskTime = diagnosis.getCreatedTime();
            }
            if (diagnosis.getSelfHarmRiskLevel() != null && diagnosis.getSelfHarmRiskLevel() >= highestSelfHarmRisk) {
                highestSelfHarmRisk = diagnosis.getSelfHarmRiskLevel();
                highestSelfHarmRiskTime = diagnosis.getCreatedTime();
            }
            if (diagnosis.getSuicideRiskLevel() != null && diagnosis.getSuicideRiskLevel() >= highestSuicideRisk) {
                highestSuicideRisk = diagnosis.getSuicideRiskLevel();
                highestSuicideRiskTime = diagnosis.getCreatedTime();
            }
        }

        List<String> stateChangeList = new ArrayList<>();
        for (int i = emotionDiagnosisList.size() - 1; i >= 0; i--) {
            String state = emotionDiagnosisList.get(i).getPsychologicalState();
            if (state != null && !state.isBlank()) {
                stateChangeList.add(state);
            }
        }
        String stateChangeStr = String.join(" → ", stateChangeList);

        int crisisCount = 0;
        boolean hasManualIntervene = false;
        for (EmotionDiagnosis diagnosis : emotionDiagnosisList) {
            if (diagnosis.getCrisisWarning() != null && diagnosis.getCrisisWarning() == EmotionDiagnosis.CRISIS_WARNING_YES) {
                crisisCount++;
            }
            if (!hasManualIntervene && diagnosis.getNeedManualIntervene() != null
                    && diagnosis.getNeedManualIntervene() == EmotionDiagnosis.NEED_MANUAL_INTERVENE_YES) {
                hasManualIntervene = true;
            }
        }

        return DiagnosisSummary.builder()
                .highestEmotionRisk(highestEmotionRisk == -1 ? null : highestEmotionRisk)
                .highestEmotionRiskTime(highestEmotionRiskTime)
                .highestSelfHarmRisk(highestSelfHarmRisk == -1 ? null : highestSelfHarmRisk)
                .highestSelfHarmRiskTime(highestSelfHarmRiskTime)
                .highestSuicideRisk(highestSuicideRisk == -1 ? null : highestSuicideRisk)
                .highestSuicideRiskTime(highestSuicideRiskTime)
                .stateChangeSequence(stateChangeStr.isEmpty() ? null : stateChangeStr)
                .crisisCount(crisisCount)
                .hasManualIntervene(hasManualIntervene)
                .build();
    }

    /**
     * 步骤2.4：干预方案维度
     * <p>
     * 汇总过往使用的干预手段、方案内容及执行情况，包括：
     * <ul>
     *   <li><b>建议汇总</b>：按自助/社会支持/专业干预三类，将历史建议整条存入（不做拆分），
     *       每条非空建议均作为独立记录追加，不做文本去重</li>
     *   <li><b>反馈标记</b>：结合用户认同反馈（agreeSuggestion*）与采纳情况（useSuggestion），
     *       标记每条建议的反馈值：true-认同、false-不认同、null-未反馈；
     *       采纳情况直接取 useSuggestion 原值</li>
     *   <li><b>优先级统计</b>：取历史 suggestion_priority 最大值，判断用户整体所需干预层级</li>
     *   <li><b>最近一次各类建议原始内容</b>：取列表首条（即最近一次）诊断记录中的原始建议文本</li>
     * </ul>
     *
     * @param emotionDiagnosisList 历史诊断结果集合（按创建时间降序）
     * @return 干预方案维度分析结果
     * @see InterventionHistory
     */
    private InterventionHistory buildInterventionHistory(List<EmotionDiagnosis> emotionDiagnosisList) {
        List<InterventionHistory.InterventionRecord> selfHelpList = new ArrayList<>();
        List<InterventionHistory.InterventionRecord> socialSupportList = new ArrayList<>();
        List<InterventionHistory.InterventionRecord> professionalList = new ArrayList<>();

        int highestPriority = -1;
        for (EmotionDiagnosis diagnosis : emotionDiagnosisList) {
            mergeInterventionRecord(selfHelpList, diagnosis.getSelfHelpSuggestion(),
                    diagnosis.getAgreeSuggestionSelf(), diagnosis.getUseSuggestion(), diagnosis.getCreatedTime());
            mergeInterventionRecord(socialSupportList, diagnosis.getSocialSupportSuggestion(),
                    diagnosis.getAgreeSuggestionSocial(), diagnosis.getUseSuggestion(), diagnosis.getCreatedTime());
            mergeInterventionRecord(professionalList, diagnosis.getProfessionalInterveneSuggestion(),
                    diagnosis.getAgreeSuggestionProfessional(), diagnosis.getUseSuggestion(), diagnosis.getCreatedTime());

            Integer priority = diagnosis.getSuggestionPriority();
            if (priority != null && priority > highestPriority) {
                highestPriority = priority;
            }
        }

        EmotionDiagnosis latestDiagnosis = emotionDiagnosisList.get(0);

        return InterventionHistory.builder()
                .selfHelpSuggestions(selfHelpList)
                .socialSupportSuggestions(socialSupportList)
                .professionalSuggestions(professionalList)
                .highestSuggestionPriority(highestPriority == -1 ? null : highestPriority)
                .latestSelfHelpSuggestion(latestDiagnosis.getSelfHelpSuggestion())
                .latestSocialSupportSuggestion(latestDiagnosis.getSocialSupportSuggestion())
                .latestProfessionalSuggestion(latestDiagnosis.getProfessionalInterveneSuggestion())
                .build();
    }

    /**
     * 步骤2.5：关键风险点维度
     * <p>
     * 遍历历史诊断记录，仅保留经 {@link #isHighRisk} 判定为高风险的诊断，
     * 提取风险相关信息，跟踪风险变化趋势，包括：
     * <ul>
     *   <li>风险细节描述（riskDetail）</li>
     *   <li>历史核心触发场景（coreTriggerScene）与关键词（coreTriggerKeywords）</li>
     *   <li>首次触发事件描述（firstTriggerDesc）与触发轮次（triggerRoundNum）</li>
     *   <li>对应对话轮次（roundNum）与诊断时间（diagnosisTime）</li>
     * </ul>
     *
     * @param emotionDiagnosisList 历史诊断结果集合（按创建时间降序）
     * @return 关键风险点列表
     * @see RiskPoints
     */
    private List<RiskPoints> buildRiskPointsList(List<EmotionDiagnosis> emotionDiagnosisList) {
        List<RiskPoints> riskPointsList = new ArrayList<>();

        for (EmotionDiagnosis diagnosis : emotionDiagnosisList) {
            if (!isHighRisk(diagnosis)) {
                continue;
            }
            RiskPoints riskPoint = RiskPoints.builder()
                    .riskDetail(diagnosis.getRiskDetail())
                    .roundNum(diagnosis.getRoundNum())
                    .diagnosisTime(diagnosis.getCreatedTime())
                    .coreTriggerScene(diagnosis.getCoreTriggerScene())
                    .coreTriggerKeywords(diagnosis.getCoreTriggerKeywords())
                    .triggerRoundNum(diagnosis.getTriggerRoundNum())
                    .firstTriggerDesc(diagnosis.getFirstTriggerDesc())
                    .build();
            riskPointsList.add(riskPoint);
        }
        return riskPointsList;
    }

    /**
     * 判断单条诊断记录是否属于高风险
     * <p>
     * 满足以下任一条件即判定为高风险：
     * <ul>
     *   <li>情绪风险等级为 HIGH 或 CRITICAL</li>
     *   <li>自伤风险等级 ≥ HIGH</li>
     *   <li>自杀风险等级 ≥ HIGH</li>
     * </ul>
     *
     * @param diagnosis 诊断记录，为 null 时返回 false
     * @return true-高风险，false-非高风险或入参为 null
     */
    private boolean isHighRisk(EmotionDiagnosis diagnosis) {
        if (diagnosis == null) {
            return false;
        }
        Integer emotionRisk = diagnosis.getEmotionRiskLevel();
        if (emotionRisk != null && (emotionRisk == EmotionDiagnosis.EMOTION_RISK_HIGH || emotionRisk == EmotionDiagnosis.EMOTION_RISK_CRITICAL)) {
            return true;
        }
        Integer selfHarmRisk = diagnosis.getSelfHarmRiskLevel();
        if (selfHarmRisk != null && (selfHarmRisk >= EmotionDiagnosis.SELF_HARM_RISK_HIGH)) {
            return true;
        }
        Integer suicideRisk = diagnosis.getSuicideRiskLevel();
        return suicideRisk != null && (suicideRisk >= EmotionDiagnosis.SUICIDE_RISK_HIGH);
    }

    /**
     * 拆分症状标签字符串为标签列表
     * <p>
     * 以英文逗号分隔，去除首尾空白并过滤空串。入参为 null 或空白时返回空列表。
     *
     * @param symptomTags 症状标签原始字符串，如 "焦虑,失眠,食欲下降"
     * @return 拆分后的标签列表
     */
    private List<String> splitSymptomTags(String symptomTags) {
        if (symptomTags == null || symptomTags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(symptomTags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 按 value 降序排序 Map，返回保持插入顺序的 {@link LinkedHashMap}
     *
     * @param map 待排序的 Map
     * @return 按 value 降序排列的 LinkedHashMap
     */
    private Map<String, Integer> sortByValueDescending(Map<String, Integer> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    /**
     * 从诊断记录集合中提取所有症状标签，去重后返回 {@link LinkedHashSet}
     *
     * @param diagnosisList 诊断记录集合
     * @return 去重后的症状标签集合
     */
    private Set<String> extractSymptomSet(List<EmotionDiagnosis> diagnosisList) {
        Set<String> symptomSet = new LinkedHashSet<>();
        for (EmotionDiagnosis diagnosis : diagnosisList) {
            symptomSet.addAll(splitSymptomTags(diagnosis.getSymptomTags()));
        }
        return symptomSet;
    }

    /**
     * 统计诊断记录集合中各症状标签的出现次数
     *
     * @param diagnosisList 诊断记录集合
     * @return 症状标签到出现次数的映射
     */
    private Map<String, Integer> countSymptoms(List<EmotionDiagnosis> diagnosisList) {
        Map<String, Integer> countMap = new LinkedHashMap<>();
        for (EmotionDiagnosis diagnosis : diagnosisList) {
            List<String> tags = splitSymptomTags(diagnosis.getSymptomTags());
            for (String tag : tags) {
                countMap.merge(tag, 1, Integer::sum);
            }
        }
        return countMap;
    }

    /**
     * 将单条建议整条追加进目标清单
     * <p>
     * 建议文本为 null 或空白时跳过；否则构建新的干预记录追加到目标清单末尾，
     * 不做文本去重与反馈合并。认同反馈 agree 转换规则：
     * agree == {@link EmotionDiagnosis#AGREE_YES} 时为 true，agree 非 null 时为 false，agree 为 null 时为 null。
     *
     * @param target         目标建议清单
     * @param suggestionText 建议原始文本（整条存入，不做拆分）
     * @param agree          用户对该类建议的认同反馈（0-不认同/1-认同/null-未反馈）
     * @param useSuggestion  用户对本次诊断建议的采纳情况（0-未尝试/1-部分/2-全部/null-未反馈）
     * @param diagnosisTime  本次诊断时间
     */
    private void mergeInterventionRecord(List<InterventionHistory.InterventionRecord> target,
                                         String suggestionText, Integer agree,
                                         Integer useSuggestion, LocalDateTime diagnosisTime) {
        if (suggestionText == null || suggestionText.isBlank()) {
            return;
        }
        Boolean agreed = null;
        if (agree != null) {
            agreed = agree == EmotionDiagnosis.AGREE_YES;
        }
        target.add(InterventionHistory.InterventionRecord.builder()
                .suggestionText(suggestionText)
                .agreed(agreed)
                .useStatus(useSuggestion)
                .diagnosisTime(diagnosisTime)
                .build());
    }

    @Override
    public Object inputSummary(OverAllState state) {
        return state.value(ConversationProcessContextBO.NAME).orElse(null);
    }

    @Override
    public Object outputSummary(OverAllState state) {
        Optional<InputResult> irOpt = state.value(InputResult.NAME);
        return irOpt.map(ir -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("historyDiagnosisSummaryResult", ir.getHistoryDiagnosisSummaryResult());
            return map;
        }).orElse(null);
    }
}