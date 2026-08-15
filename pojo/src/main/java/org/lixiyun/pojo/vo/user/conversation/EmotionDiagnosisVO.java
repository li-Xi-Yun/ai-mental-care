package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 情感诊断书 VO（接口返回）
 * @author lixiyun
 * @since 2026-03-19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "情感诊断书响应VO")
public class EmotionDiagnosisVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "诊断书主键ID")
    private Long id;

    @Schema(description = "会话ID")
    private Long conversationId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "该诊断数据创建或更新时的轮次")
    private Integer roundNum;

    @Schema(description = "诊断书核心内容（自然语言总结）")
    private String diagnosisContent;

    @Schema(description = "核心情绪标签（原始字符串，如焦虑）")
    private String coreEmotionLabel;

    @Schema(description = "核心情绪平均置信度（0-1）")
    private BigDecimal coreEmotionConfAvg;

    @Schema(description = "核心情绪强度分值(0-1)")
    private BigDecimal coreEmotionIntensityScore;

    @Schema(description = "核心情绪标签列表（拆分后，前端展示用）")
    private Map<String, BigDecimal> coreEmotion;

    @Schema(description = "次要情绪 key:情绪标签 value:置信度")
    private Map<String, BigDecimal> secondaryEmotion;



    @Schema(description = "负向情绪占比（0-1）")
    private BigDecimal negativeEmotionRatio;

    @Schema(description = "正向情绪占比（0-1）")
    private BigDecimal positiveEmotionRatio;

    @Schema(description = "中性情绪占比（0-1，三者总和为1）")
    private BigDecimal neutralEmotionRatio;

    @Schema(description = "负向情绪细分占比")
    private Map<String, BigDecimal> negativeEmotionDetail;

    @Schema(description = "正向情绪细分占比")
    private Map<String, BigDecimal> positiveEmotionDetail;



    @Schema(description = "整体情绪趋势编码：0-上升/1-下降/2-平稳/3-无法判断")
    private Integer emotionTrend;

    @Schema(description = "情绪峰值轮次（核心情绪强度最高的轮次）")
    private Integer emotionPeakRound;

    @Schema(description = "情绪低谷轮次（核心情绪强度最低的轮次）")
    private Integer emotionValleyRound;

    @Schema(description = "情绪波动幅度（峰值-谷值的置信度差）")
    private BigDecimal emotionFluctuationAmplitude;

    @Schema(description = "情绪平稳的轮次数量")
    private Integer emotionStableRounds;

    @Schema(description = "情绪稳定性得分（0-1，越高越稳定）")
    private BigDecimal emotionStabilityScore;

    @Schema(description = "整体PAD愉悦度均值，值域[-1,1]")
    private BigDecimal avgP;

    @Schema(description = "整体PAD唤醒度均值，值域[-1,1]")
    private BigDecimal avgA;

    @Schema(description = "整体PAD支配度均值，值域[-1,1]")
    private BigDecimal avgD;

    @Schema(description = "P维度标准差，数值越大愉悦度波动越强")
    private BigDecimal stdP;

    @Schema(description = "A维度标准差，数值越大唤醒起伏剧烈")
    private BigDecimal stdA;

    @Schema(description = "D维度标准差，数值越大掌控感反复变化")
    private BigDecimal stdD;



    @Schema(description = "核心触发场景（如工作压力/人际关系/家庭矛盾）")
    private String coreTriggerScene;

    @Schema(description = "核心触发关键词，多个逗号分隔")
    private String coreTriggerKeywords;

    @Schema(description = "首次出现核心触发因素的轮次")
    private Integer triggerRoundNum;



    @Schema(description = "整体心理状态评估")
    private String psychologicalState;

    @Schema(description = "核心症状总结（自然语言）")
    private String symptomSummary;

    @Schema(description = "症状标签集合，逗号分隔")
    private String symptomTags;



    @Schema(description = "社会功能受损程度：无影响/轻度受损/中度受损/重度受损")
    private String socialFunctionImpact;

    @Schema(description = "受影响的具体领域，逗号分隔")
    private String impactDomains;

    @Schema(description = "对日常生活影响的自然语言描述")
    private String dailyLifeInfluence;



    @Schema(description = "症状持续时长")
    private String symptomDuration;

    @Schema(description = "发作模式：持续性/阵发性/偶发/逐渐加重/反复波动")
    private String onsetPattern;

    @Schema(description = "用户提及的首次触发事件/原因")
    private String firstTriggerDesc;



    @Schema(description = "社会支持水平：0-良好/1-一般/2-较差/3-匮乏/4-无法判断")
    private Integer socialSupportLevel;

    @Schema(description = "保护性因素/心理资源，逗号分隔")
    private String protectiveFactors;

    @Schema(description = "用户的应对方式")
    private String copingStyle;



    @Schema(description = "情绪风险等级编码：0-低/1-中/2-高/3-危急/4-无法判断")
    private Integer emotionRiskLevel;

    @Schema(description = "情绪调节建议")
    private String emotionAdjustSuggestion;

    @Schema(description = "是否需要人工干预：0-否，1-是")
    private Integer needManualIntervene;

    @Schema(description = "自伤风险等级：0-无/1-低/2-中/3-高/4-极高/5-无法判断")
    private Integer selfHarmRiskLevel;

    @Schema(description = "自杀风险等级：0-无/1-低/2-中/3-高/4-极高/5-无法判断")
    private Integer suicideRiskLevel;

    @Schema(description = "风险细节描述")
    private String riskDetail;

    @Schema(description = "是否触发危机预警：0-否 1-是")
    private Integer crisisWarning;



    @Schema(description = "自助调节建议")
    private String selfHelpSuggestion;

    @Schema(description = "社会支持建议")
    private String socialSupportSuggestion;

    @Schema(description = "专业干预建议")
    private String professionalInterveneSuggestion;

    @Schema(description = "建议优先级：1-自助为主 2-建议寻求支持 3-强烈建议专业干预 4-无法判断")
    private Integer suggestionPriority;



    @Schema(description = "用户对本次诊断打分 1~5分，null代表未评分")
    private Integer diagnosisScore;

    @Schema(description = "用户文字反馈")
    private String feedbackContent;

    @Schema(description = "是否认同风险评估：0-不认同 1-认同 null未反馈")
    private Integer agreeRiskJudge;

    @Schema(description = "是否认同自助调节建议：0-不认同 1-认同 null未反馈")
    private Integer agreeSuggestionSelf;

    @Schema(description = "是否认同社会支持建议：0-不认同 1-认同 null未反馈")
    private Integer agreeSuggestionSocial;

    @Schema(description = "是否认同专业干预建议：0-不认同 1-认同 null未反馈")
    private Integer agreeSuggestionProfessional;

    @Schema(description = "是否尝试采纳建议：0-没有 1-尝试部分 2-全部尝试 null未反馈")
    private Integer useSuggestion;



    @Schema(description = "用户提交反馈时间")
    private LocalDateTime feedbackTime;

    @Schema(description = "记录创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updatedTime;

}
