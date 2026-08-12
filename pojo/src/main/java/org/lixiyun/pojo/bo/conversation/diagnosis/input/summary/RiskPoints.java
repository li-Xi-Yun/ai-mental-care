package org.lixiyun.pojo.bo.conversation.diagnosis.input.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 历史诊断摘要-风险点
 *
 * @author lixiyun
 * @since 2026-08-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskPoints implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 风险细节描述
     */
    private String riskDetail;

    /**
     * 历史核心触发场景
     */
    private String coreTriggerScene;

    /**
     * 核心触发关键词（多个用逗号分隔，如"加班,吵架,失业"）
     */
    private String coreTriggerKeywords;

    /**
     * 首次出现核心触发因素的轮次
     */
    private Integer triggerRoundNum;

    /**
     * 用户提及的首次触发事件/原因
     */
    private String firstTriggerDesc;

    /**
     * 该诊断数据对应的轮次
     */
    private Integer roundNum;

    /**
     * 对应的诊断时间
     */
    private LocalDateTime diagnosisTime;

    public static String getPrompt() {
        return "riskDetail：风险细节描述，" +
                "coreTriggerScene：历史核心触发场景，" +
                "coreTriggerKeywords：核心触发关键词（多个用逗号分隔），" +
                "triggerRoundNum：首次出现核心触发因素的轮次，" +
                "firstTriggerDesc：用户提及的首次触发事件/原因，" +
                "roundNum：该诊断数据对应的轮次，" +
                "diagnosisTime：对应的诊断时间";
    }
}