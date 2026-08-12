package org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 情绪强度统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntensityStat implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 强度平均值（0~1）
     */
    private BigDecimal avg;

    /**
     * 强度峰值（最大值）
     */
    private BigDecimal peak;

    /**
     * 强度峰值对应的轮次号
     */
    private Integer peakRound;

    /**
     * 强度谷值（最小值）
     */
    private BigDecimal valley;

    /**
     * 强度谷值对应的轮次号
     */
    private Integer valleyRound;

    /**
     * 波动幅度 = 峰值 - 谷值
     */
    private BigDecimal waveRange;

    public static String getPrompt() {
        return "avg：强度平均值（0~1），" +
                "peak：强度峰值，" +
                "peakRound：强度峰值对应的轮次号，" +
                "valley：强度谷值，" +
                "valleyRound：强度谷值对应的轮次号，" +
                "waveRange：波动幅度（峰值-谷值）";
    }
}