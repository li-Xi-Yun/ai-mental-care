package org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * PAD三维度通用统计字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PadDimension implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 全会话平均值
     */
    private BigDecimal avg;

    /**
     * 最大值（峰值）
     */
    private BigDecimal max;

    /**
     * 最小值（谷值）
     */
    private BigDecimal min;

    /**
     * 总体标准差（衡量波动程度）
     */
    private BigDecimal std;

    /**
     * 波动幅度 = 最大值 - 最小值
     */
    private BigDecimal waveRange;

    /**
     * 最新一轮对话的维度数值
     */
    private BigDecimal latest;

    public static String getPrompt() {
        return "avg：全会话平均值，" +
                "max：最大值（峰值），" +
                "min：最小值（谷值），" +
                "std：总体标准差（衡量波动程度），" +
                "waveRange：波动幅度（最大值-最小值），" +
                "latest：最新一轮对话的维度数值";
    }
}