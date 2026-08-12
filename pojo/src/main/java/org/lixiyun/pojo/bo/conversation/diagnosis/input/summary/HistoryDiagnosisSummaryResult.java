package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 历史诊断摘要聚合结果
 * <p>包含基础元信息及五大维度聚合结果</p>
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryDiagnosisSummaryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否首次诊断
     */
    private Boolean isFirstDiagnosis;

    /**
     * 首次诊断提示词
     */
    private String firstDiagnosisPrompt;

    /**
     * 历史诊断总次数
     */
    private Integer historyCount;

    /**
     * 最近一次诊断时间
     */
    private LocalDateTime lastDiagnosisTime;

    /**
     * 症状演变维度
     */
    private SymptomEvolution symptomEvolution;

    /**
     * 情绪趋势维度
     */
    private EmotionTrend emotionTrend;

    /**
     * 诊断概要维度
     */
    private DiagnosisSummary diagnosisSummary;

    /**
     * 干预历史维度
     */
    private InterventionHistory interventionHistory;

    /**
     * 风险点列表
     */
    private List<RiskPoints> riskPointsList;

    public static String getPrompt() {
        return "isFirstDiagnosis：是否首次诊断，" +
                "firstDiagnosisPrompt：首次诊断提示词，" +
                "historyCount：历史诊断总次数，" +
                "lastDiagnosisTime：最近一次诊断时间，" +
                "symptomEvolution：" + SymptomEvolution.getPrompt() + "，" +
                "emotionTrend：" + EmotionTrend.getPrompt() + "，" +
                "diagnosisSummary：" + DiagnosisSummary.getPrompt() + "，" +
                "interventionHistory：" + InterventionHistory.getPrompt() + "，" +
                "riskPointsList：" + RiskPoints.getPrompt();
    }
}