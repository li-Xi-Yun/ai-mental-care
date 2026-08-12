package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 情绪趋势-基础情绪概况
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionBasicInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 历史主导情绪标签
     */
    private String dominantEmotion;

    /**
     * 整体情绪趋势：逐步好转/持续加重/波动反复/无法判断
     */
    private String overallTrend;

    /**
     * 整体情绪稳定性平均得分（0-1）
     */
    private BigDecimal avgStabilityScore;

    /**
     * 情绪最差节点描述
     */
    private String worstEmotionNode;

    /**
     * 历史所有情绪标签列表（衍生字段）
     */
    private List<String> allEmotionLabels;

    /**
     * 高频情绪Top3（含主次情绪）
     */
    private List<String> topEmotionList;

    public static String getPrompt() {
        return "dominantEmotion：历史主导情绪标签，" +
                "overallTrend：整体情绪趋势（逐步好转/持续加重/波动反复/无法判断），" +
                "avgStabilityScore：整体情绪稳定性平均得分（0-1），" +
                "worstEmotionNode：情绪最差节点描述，" +
                "allEmotionLabels：历史所有情绪标签列表，" +
                "topEmotionList：高频情绪Top3";
    }
}