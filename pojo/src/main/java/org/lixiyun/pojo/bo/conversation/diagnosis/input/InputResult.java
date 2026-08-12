package org.lixiyun.pojo.bo.conversation.diagnosis.input;

import lombok.Builder;
import lombok.Data;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.SessionCleanResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization.SymptomNormalizeResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.EmotionStatisticsResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.CoreInfoExtractResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.HistoryDiagnosisSummaryResult;

import java.io.Serializable;

/**
 * 输入侧-所有结果集合
 * @author lixiyun
 * @since 2026-08-10 14:11
 */
@Data
@Builder
public class InputResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NAME = "inputResult";

    /**
     * 数据预清洗结果
     */
    private SessionCleanResult sessionCleanResult;

    /**
     * 消息结构化处理结果
     */
    private CoreInfoExtractResult coreInfoExtractResult;

    /**
     * 情绪数据处理结果
     */
    private EmotionStatisticsResult emotionStatisticsResult;

    /**
     * 语义归一化处理结果
     */
    private SymptomNormalizeResult symptomNormalizeResult;

    /**
     * 历史诊断摘要聚合结果
     */
    private HistoryDiagnosisSummaryResult historyDiagnosisSummaryResult;

    public static String getPrompt() {
        return "sessionCleanResult：" + SessionCleanResult.getPrompt() + "，" +
                "coreInfoExtractResult：" + CoreInfoExtractResult.getPrompt() + "，" +
                "emotionStatisticsResult：" + EmotionStatisticsResult.getPrompt() + "，" +
                "symptomNormalizeResult：" + SymptomNormalizeResult.getPrompt() + "，" +
                "historyDiagnosisSummaryResult：" + HistoryDiagnosisSummaryResult.getPrompt();
    }
}