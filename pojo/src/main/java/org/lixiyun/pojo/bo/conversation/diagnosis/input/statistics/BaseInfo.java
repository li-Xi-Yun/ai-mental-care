package org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 基础情绪分布信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseInfo implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 全会话主导情绪主标签
     */
    private EmotionDistributionItem dominantEmotion;

    /**
     * 主情绪标签分布列表（按占比降序）
     */
    private List<EmotionDistributionItem> emotionDistribution;

    /**
     * 细分情绪标签分布列表（按占比降序）
     */
    private List<SubEmotionDistributionItem> subEmotionDistribution;

    /**
     * 全会话平均正向情绪占比（0~1）
     */
    private BigDecimal avgPositiveRatio;

    /**
     * 全会话平均中性情绪占比（0~1）
     */
    private BigDecimal avgNeutralRatio;

    /**
     * 全会话平均负向情绪占比（0~1）
     */
    private BigDecimal avgNegativeRatio;

    /**
     * 全会话情绪识别平均置信度（0~1）
     */
    private BigDecimal avgConfidence;

    public static String getPrompt() {
        return "dominantEmotion：全会话主导情绪主标签，" + EmotionDistributionItem.getPrompt() + "，" +
                "emotionCount：全会话出现的情绪主标签种类数，" +
                "emotionDistribution：" + EmotionDistributionItem.getPrompt() + "，" +
                "subEmotionDistribution：" + SubEmotionDistributionItem.getPrompt() + "，" +
                "avgPositiveRatio：全会话平均正向情绪占比（0~1），" +
                "avgNeutralRatio：全会话平均中性情绪占比（0~1），" +
                "avgNegativeRatio：全会话平均负向情绪占比（0~1），" +
                "avgConfidence：全会话情绪识别平均置信度（0~1）";
    }
}