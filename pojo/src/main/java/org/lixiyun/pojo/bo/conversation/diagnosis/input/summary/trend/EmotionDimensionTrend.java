package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 情绪趋势-维度变化趋势
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionDimensionTrend implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 愉悦度变化趋势
     */
    private String pleasureTrend;

    /**
     * 唤醒度变化趋势
     */
    private String arousalTrend;

    /**
     * 支配度变化趋势
     */
    private String dominanceTrend;

    public static String getPrompt() {
        return "pleasureTrend：愉悦度变化趋势，" +
                "arousalTrend：唤醒度变化趋势，" +
                "dominanceTrend：支配度变化趋势";
    }
}