package org.lixiyun.pojo.bo.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-08-10 10:05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionAnalysisBO implements Serializable {

    private static final long serialVersionUID = -35884017703413638L;

    /**
     * 情感分析详情
     */
    private String analysisContent;

    /**
     * 情感标签（如anger/开心/neutral/不满等）
     */
    private String emotionLabel;

    /**
     * 情感细分标签（如愤怒可细分“不满/暴怒/抱怨”）
     */
    private String emotionSubLabel;

    /**
     * 情感识别置信度（0-1，如0.9200）
     */
    private Double emotionConfidence;

    /**
     * 情绪本身的强烈程度（0-1，如0.9200）
     */
    private Double emotionIntensity;

    /**
     * 较上一轮的情绪变化趋势
     */
    private String emotionTrend;

    /**
     * PAD愉悦度P，取值范围[-1,1]
     */
    private Double pScore;

    /**
     * PAD唤醒度A，取值范围[-1,1]
     */
    private Double aScore;

    /**
     * PAD支配度D，取值范围[-1,1]
     */
    private Double dScore;

    /**
     * 负向情绪占比（0-1）
     */
    private Double negativeEmotionRatio;

    /**
     * 中性情绪占比（0-1）
     */
    private Double neutralEmotionRatio;

    /**
     * 正向情绪占比（0-1）
     */
    private Double positiveEmotionRatio;

    @Override
    public String toString() {
        return "EmotionAnalysisBO{" +
                "情感分析详情='" + analysisContent + '\'' +
                ", 情感标签='" + emotionLabel + '\'' +
                ", 情感细分标签='" + emotionSubLabel + '\'' +
                ", 情感识别置信度=" + emotionConfidence +
                ", 情绪强烈程度=" + emotionIntensity +
                ", 情绪变化趋势='" + emotionTrend + '\'' +
                ", PAD愉悦度P=" + pScore +
                ", PAD唤醒度A=" + aScore +
                ", PAD支配度D=" + dScore +
                ", 负向情绪占比=" + negativeEmotionRatio +
                ", 中性情绪占比=" + neutralEmotionRatio +
                ", 正向情绪占比=" + positiveEmotionRatio +
                '}';
    }

    public static String getPrompt() {
            return "analysisContent：情感分析详情，" +
                    "emotionLabel：情感标签（如anger/开心/neutral/不满等），" +
                    "emotionSubLabel：情感细分标签（如愤怒可细分“不满/暴怒/抱怨”），" +
                    "emotionConfidence：情感识别置信度（0-1，如0.9200），" +
                    "emotionIntensity：情绪本身的强烈程度（0-1，如0.9200），" +
                    "emotionTrend：较上一轮的情绪变化趋势，" +
                    "pScore：PAD愉悦度P，取值范围[-1,1]，" +
                    "aScore：PAD唤醒度A，取值范围[-1,1]，" +
                    "dScore：PAD支配度D，取值范围[-1,1]，" +
                    "negativeEmotionRatio：负向情绪占比（0-1），" +
                    "neutralEmotionRatio：中性情绪占比（0-1），" +
                    "positiveEmotionRatio：正向情绪占比（0-1）";
    }

}