package org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 量化指标统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quantitative implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * P维度（愉悦度）统计结果
     */
    private PadDimension p;

    /**
     * A维度（唤醒度）统计结果
     */
    private PadDimension a;

    /**
     * D维度（支配度）统计结果
     */
    private PadDimension d;

    /**
     * 全局情绪强度统计结果
     */
    private IntensityStat intensity;

    public static String getPrompt() {
        return "p：" + PadDimension.getPrompt() + "，" +
                "a：" + PadDimension.getPrompt() + "，" +
                "d：" + PadDimension.getPrompt() + "，" +
                "intensity：" + IntensityStat.getPrompt();
    }
}