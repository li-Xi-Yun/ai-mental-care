package org.lixiyun.pojo.vo.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author lixiyun
 * @since 2026-03-19 27:53
 */
@Data
@Schema(description = "情感诊断书 VO")
public class EmotionDiagnosisVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "诊断书主键 ID")
    private Long id;

    @Schema(description = "会话 ID")
    private Long conversationId;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "该诊断数据创建或更新时的轮次")
    private Integer roundNum;

    @Schema(description = "诊断书核心内容（自然语言总结）")
    private String diagnosisContent;

    @Schema(description = "核心情绪标签（如焦虑/抑郁/开心/中性）")
    private List<String> coreEmotionLabelList;

    @Schema(description = "核心情绪平均置信度（0-1，精度 0.000-1.000）")
    private BigDecimal coreEmotionConfAvg;

    @Schema(description = "核心情绪强度（轻度/中度/重度/极重度）")
    private String coreEmotionIntensity;

    @Schema(description = """
            次要情绪
            key:次要情绪标签（多个，如"烦躁,委屈,孤独"）;
            value:次要情绪置信度（与次要标签一一对应，如"0.85,0.72,0.68"）
            """)
    private Map<String, BigDecimal> secondaryEmotion;

    @Schema(description = "负向情绪占比（0-1）")
    private BigDecimal negativeEmotionRatio;

    @Schema(description = "正向情绪占比（0-1）")
    private BigDecimal positiveEmotionRatio;

    @Schema(description = "中性情绪占比（0-1，三者和为 1）")
    private BigDecimal neutralEmotionRatio;

    @Schema(description = "负向情绪细分占比，如{\"焦虑\":0.45,\"愤怒\":0.25,\"悲伤\":0.10}")
    private Map<String, BigDecimal> negativeEmotionDetail;

    @Schema(description = "正向情绪细分占比，如{\"开心\":0.30,\"欣慰\":0.15,\"放松\":0.05}")
    private Map<String, BigDecimal> positiveEmotionDetail;

    @Schema(description = "整体情绪趋势")
    private String emotionTrend;

    @Schema(description = "情绪峰值轮次（核心情绪强度最高的轮次）")
    private Integer emotionPeakRound;

    @Schema(description = "情绪低谷轮次（核心情绪强度最低的轮次）")
    private Integer emotionValleyRound;

    @Schema(description = "情绪波动幅度（峰值 - 谷值的置信度差）")
    private BigDecimal emotionFluctuationAmplitude;

    @Schema(description = "情绪平稳的轮次数量（占总轮次的辅助指标）")
    private Integer emotionStableRounds;

    @Schema(description = "核心触发场景（如工作压力/人际关系/家庭矛盾）")
    private String coreTriggerScene;

    @Schema(description = "核心触发关键词（多个用逗号分隔，如加班，吵架，失业）")
    private String coreTriggerKeywords;

    @Schema(description = "首次出现核心触发因素的轮次")
    private Integer triggerRoundNum;

    @Schema(description = "情绪风险等级（低/中/高/危急）")
    private String emotionRiskLevel;

    @Schema(description = "情绪调节建议（自然语言，如建议适当休息，减少加班频率）")
    private String emotionAdjustSuggestion;

    @Schema(description = "是否需要人工干预（0-否，1-是）")
    private Integer needManualIntervene;

    @Schema(description = "记录创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updatedTime;

}
