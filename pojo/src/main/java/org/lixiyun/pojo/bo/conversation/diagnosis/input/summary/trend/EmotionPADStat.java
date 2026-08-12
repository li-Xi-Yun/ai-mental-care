package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 情绪趋势-PAD三维统计数据
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionPADStat implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 历史平均愉悦度P
     */
    private BigDecimal avgP;

    /**
     * 历史平均唤醒度A
     */
    private BigDecimal avgA;

    /**
     * 历史平均支配度D
     */
    private BigDecimal avgD;

    /**
     * P维度历史标准差
     */
    private BigDecimal stdP;

    /**
     * A维度历史标准差
     */
    private BigDecimal stdA;

    /**
     * D维度历史标准差
     */
    private BigDecimal stdD;

    public static String getPrompt() {
        return "avgP：历史平均愉悦度P，" +
                "avgA：历史平均唤醒度A，" +
                "avgD：历史平均支配度D，" +
                "stdP：P维度历史标准差，" +
                "stdA：A维度历史标准差，" +
                "stdD：D维度历史标准差";
    }
}