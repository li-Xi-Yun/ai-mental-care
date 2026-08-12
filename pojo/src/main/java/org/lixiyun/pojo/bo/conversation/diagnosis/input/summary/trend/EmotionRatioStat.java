package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 情绪趋势-情绪占比统计
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionRatioStat implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 历史平均负向情绪占比
     */
    private BigDecimal avgNegativeRatio;

    /**
     * 历史平均正向情绪占比
     */
    private BigDecimal avgPositiveRatio;

    /**
     * 历史平均中性情绪占比
     */
    private BigDecimal avgNeutralRatio;

    /**
     * 高频负向细分情绪Top3
     */
    private List<String> topNegativeEmotions;

    public static String getPrompt() {
        return "avgNegativeRatio：历史平均负向情绪占比，" +
                "avgPositiveRatio：历史平均正向情绪占比，" +
                "avgNeutralRatio：历史平均中性情绪占比，" +
                "topNegativeEmotions：高频负向细分情绪Top3";
    }
}