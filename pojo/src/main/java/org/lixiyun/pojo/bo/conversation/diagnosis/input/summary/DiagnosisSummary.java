package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 历史诊断摘要-诊断概要
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 历史最高自杀风险等级
     */
    private Integer highestSuicideRisk;

    /**
     * 历史最高情绪风险等级
     */
    private Integer highestEmotionRisk;

    /**
     * 历史最高自伤风险等级
     */
    private Integer highestSelfHarmRisk;

    /**
     * 心理状态变化序列
     */
    private String stateChangeSequence;

    /**
     * 历史危机预警次数
     */
    private Integer crisisCount;

    /**
     * 是否有过人工干预记录
     */
    private Boolean hasManualIntervene;

    /**
     * 历史最高情绪风险出现时的时间
     */
    private LocalDateTime highestEmotionRiskTime;

    /**
     * 历史最高自伤风险出现时的时间
     */
    private LocalDateTime highestSelfHarmRiskTime;

    /**
     * 历史最高自杀风险出现时的时间
     */
    private LocalDateTime highestSuicideRiskTime;

    public static String getPrompt() {
        return "highestSuicideRisk：历史最高自杀风险等级，" +
                "highestEmotionRisk：历史最高情绪风险等级，" +
                "highestSelfHarmRisk：历史最高自伤风险等级，" +
                "stateChangeSequence：心理状态变化序列，" +
                "crisisCount：历史危机预警次数，" +
                "hasManualIntervene：是否有过人工干预记录，" +
                "highestEmotionRiskTime：历史最高情绪风险出现时的时间，" +
                "highestSelfHarmRiskTime：历史最高自伤风险出现时的时间，" +
                "highestSuicideRiskTime：历史最高自杀风险出现时的时间";
    }
}