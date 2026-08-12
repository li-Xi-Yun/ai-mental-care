package org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 情绪动态趋势特征
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trend implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 全会话情绪整体趋势
     * 枚举值：上升/下降/平稳/无法判断
     */
    private String emotionTrend;

    /**
     * 情绪强度峰值对应的轮次号
     */
    private Integer emotionPeakRound;

    /**
     * 情绪强度谷值对应的轮次号
     */
    private Integer emotionValleyRound;

    /**
     * 情绪波动幅度（峰值与谷值的差值，0~1）
     */
    private BigDecimal emotionFluctuationAmplitude;

    /**
     * 情绪平稳的相邻轮次数量
     */
    private Integer emotionStableRounds;

    /**
     * 情绪稳定性得分（0~1，越高越稳定）
     */
    private BigDecimal emotionStabilityScore;

    /**
     * 情绪主标签变化的总次数
     */
    private Integer emotionChangeCount;

    /**
     * 情绪负向恶化的次数
     */
    private Integer negativeChangeCount;

    public static String getPrompt() {
        return "emotionTrend：全会话情绪整体趋势（上升/下降/平稳/无法判断），" +
                "emotionPeakRound：情绪强度峰值对应的轮次号，" +
                "emotionValleyRound：情绪强度谷值对应的轮次号，" +
                "emotionFluctuationAmplitude：情绪波动幅度（0~1），" +
                "emotionStableRounds：情绪平稳的相邻轮次数量，" +
                "emotionStabilityScore：情绪稳定性得分（0~1，越高越稳定），" +
                "emotionChangeCount：情绪主标签变化的总次数，" +
                "negativeChangeCount：情绪负向恶化的次数";
    }
}