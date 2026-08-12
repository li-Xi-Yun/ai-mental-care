package org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 细分情绪分布项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubEmotionDistributionItem implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 情绪标签名称
     */
    private String label;

    /**
     * 出现轮次数
     */
    private Integer count;

    /**
     * 出现占比（0~1）
     */
    private BigDecimal ratio;

    public static String getPrompt() {
        return "label：情绪标签名称，" +
                "count：出现轮次数，" +
                "ratio：出现占比（0~1）";
    }
}