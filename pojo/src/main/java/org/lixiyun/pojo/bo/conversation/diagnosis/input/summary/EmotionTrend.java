package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionBasicInfo;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionDimensionTrend;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionPADStat;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionRatioStat;

import java.io.Serializable;

/**
 * 历史诊断摘要-情绪趋势
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionTrend implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final double TREND_THRESHOLD = 0.1;
    public static final String TREND_UP = "逐步好转";
    public static final String TREND_DOWN = "持续加重";
    public static final String TREND_FLAT = "波动反复";
    public static final String TREND_UNKNOWN = "无法判断";


    /**
     * 基础情绪概况信息
     */
    private EmotionBasicInfo basicInfo;

    /**
     * PAD三维均值统计
     */
    private EmotionPADStat padStat;

    /**
     * 情绪维度变化趋势
     */
    private EmotionDimensionTrend dimensionTrend;

    /**
     * 情绪占比统计信息
     */
    private EmotionRatioStat ratioStat;

    public static String getPrompt() {
        return "basicInfo：" + EmotionBasicInfo.getPrompt() + "，" +
                "padStat：" + EmotionPADStat.getPrompt() + "，" +
                "dimensionTrend：" + EmotionDimensionTrend.getPrompt() + "，" +
                "ratioStat：" + EmotionRatioStat.getPrompt();
    }
}