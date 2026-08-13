package org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 情绪统计分析模块输出结果
 * 会话级情绪聚合统计数据包
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionStatisticsResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 趋势枚举：上升 */
    public static final String TREND_RISING = "上升";
    /** 趋势枚举：下降 */
    public static final String TREND_FALLING = "下降";
    /** 趋势枚举：平稳 */
    public static final String TREND_STABLE = "平稳";
    /** 趋势枚举：无法判断 */
    public static final String TREND_UNKNOWN = "无法判断";

    /**
     * 是否存在有效情绪数据
     * 全轮次均为无效噪声/低置信度时返回false
     */
    private Boolean hasValidData;

    /**
     * 纳入统计的有效情绪轮次总数
     * 已排除纯噪声、低置信度轮次
     */
    private Integer validRoundNum;

    /**
     * 基础情绪分布信息
     */
    private BaseInfo baseInfo;

    /**
     * 量化指标统计（PAD三维+情绪强度）
     */
    private Quantitative quantitative;

    /**
     * 情绪动态趋势特征
     */
    private Trend trend;

    public static String getPrompt() {
        return "hasValidData：是否存在有效情绪数据，" +
                "validRoundNum：纳入统计的有效情绪轮次总数，" +
                "baseInfo：" + BaseInfo.getPrompt() + "，" +
                "quantitative：" + Quantitative.getPrompt() + "，" +
                "trend：" + Trend.getPrompt();
    }
}