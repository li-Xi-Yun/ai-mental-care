package org.lixiyun.pojo.bo.conversation.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * @author lixiyun
 * @since 2026-08-13 14:33
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisData implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NAME = "DiagnosisData";

    /**
     * 诊断书核心内容（自然语言总结）
     */
    private String diagnosisContent;

    /**
     * 核心情绪标签（如焦虑/抑郁/开心/中性）
     */
    private String coreEmotionLabel;

    /**
     * 核心情绪平均置信度（0-1，精度提升）
     */
    private BigDecimal coreEmotionConfAvg;

    /**
     * 核心情绪强度分值(0-1)
     */
    private BigDecimal coreEmotionIntensityScore;

    /**
     * 次要情绪
     * <br>
     * key:次要情绪标签（多个，如"烦躁,委屈,孤独"）;
     * <br>
     * value:次要情绪置信度（与次要标签一一对应，如"0.85,0.72,0.68"）
     */
    private Map<String, BigDecimal> secondaryEmotion;

    /**
     * 负向情绪占比（0-1）
     */
    private BigDecimal negativeEmotionRatio;

    /**
     * 正向情绪占比（0-1）
     */
    private BigDecimal positiveEmotionRatio;

    /**
     * 中性情绪占比（0-1，三者和为1）
     */
    private BigDecimal neutralEmotionRatio;

    /**
     * 负向情绪细分占比,如{"焦虑":0.45,"愤怒":0.25,"悲伤":0.10}
     */
    private Map<String, BigDecimal> negativeEmotionDetail;

    /**
     * 正向情绪细分占比,如{"开心":0.30,"欣慰":0.15,"放松":0.05}
     */
    private Map<String, BigDecimal> positiveEmotionDetail;

    /**
     * 整体情绪趋势（0-上升/1-下降/2-平稳/3-无法判断）
     */
    private Integer emotionTrend;

    /**
     * 情绪峰值轮次（核心情绪强度最高的轮次）
     */
    private Integer emotionPeakRound;

    /**
     * 情绪低谷轮次（核心情绪强度最低的轮次）
     */
    private Integer emotionValleyRound;

    /**
     * 情绪波动幅度（峰值-谷值的置信度差）
     */
    private BigDecimal emotionFluctuationAmplitude;

    /**
     * 情绪平稳的轮次数量（占总轮次的辅助指标）
     */
    private Integer emotionStableRounds;

    /**
     * 情绪稳定性得分（0-1，越高越稳定）
     */
    private BigDecimal emotionStabilityScore;

    /**
     * 整体PAD愉悦度均值，值域[-1,1]
     */
    private BigDecimal avgP;

    /**
     * 整体PAD唤醒度均值，值域[-1,1]
     */
    private BigDecimal avgA;

    /**
     * 整体PAD支配度均值，值域[-1,1]
     */
    private BigDecimal avgD;

    /**
     * P维度标准差，数值越大情绪愉悦度波动越强
     */
    private BigDecimal stdP;

    /**
     * A维度标准差，数值越大唤醒起伏剧烈
     */
    private BigDecimal stdA;

    /**
     * D维度标准差，数值越大掌控感反复变化
     */
    private BigDecimal stdD;

    /**
     * 核心触发场景（如工作压力/人际关系/家庭矛盾）
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
     * 整体心理状态评估，如"适应不良/轻度焦虑状态/抑郁情绪困扰/人际敏感状态"
     */
    private String psychologicalState;

    /**
     * 核心症状总结（自然语言），如"持续情绪低落、兴趣减退、入睡困难、注意力下降"
     */
    private String symptomSummary;

    /**
     * 症状标签集合，逗号分隔，如"失眠,焦虑,自卑,易怒,兴趣减退,食欲下降"
     */
    private String symptomTags;

    /**
     * 社会功能受损程度：无影响/轻度受损/中度受损/重度受损
     */
    private String socialFunctionImpact;

    /**
     * 受影响的具体领域，逗号分隔，如"工作效率下降,睡眠受影响,社交减少,食欲变差"
     */
    private String impactDomains;

    /**
     * 对日常生活影响的自然语言描述
     */
    private String dailyLifeInfluence;

    /**
     * 症状持续时长，如"几天/1-2周/1个月以上/3个月以上/半年以上"
     */
    private String symptomDuration;

    /**
     * 发作模式：持续性/阵发性/偶发/逐渐加重/反复波动
     */
    private String onsetPattern;

    /**
     * 用户提及的首次触发事件/原因
     */
    private String firstTriggerDesc;

    /**
     * 社会支持水平：0-良好/1-一般/2-较差/3-匮乏/4-无法判断
     */
    private Integer socialSupportLevel;

    /**
     * 保护性因素/心理资源，逗号分隔，如"家人支持,朋友陪伴,有兴趣爱好,自我调节能力强"
     */
    private String protectiveFactors;

    /**
     * 用户的应对方式，如"积极解决/回避/倾诉/压抑/运动调节"
     */
    private String copingStyle;

    /**
     * 情绪风险等级（0-低/1-中/2-高/3-危急/4-无法判断）
     */
    private Integer emotionRiskLevel;

    /**
     * 情绪调节建议（自然语言，如"建议适当休息，减少加班频率"）
     */
    private String emotionAdjustSuggestion;

    /**
     * 是否需要人工干预（0-否，1-是）
     */
    private Integer needManualIntervene;

    /**
     * 自伤风险等级：0-无/1-低/2-中/3-高/4-极高/5-无法判断
     */
    private Integer selfHarmRiskLevel;

    /**
     * 自杀风险等级：0-无/1-低/2-中/3-高/4-极高/5-无法判断
     */
    private Integer suicideRiskLevel;

    /**
     * 风险细节描述，如"存在消极念头，无具体计划，无自伤行为"
     */
    private String riskDetail;

    /**
     * 是否触发危机预警：0-否 1-是
     */
    private Integer crisisWarning;

    /**
     * 自助调节建议（用户可独立完成的小事，如呼吸放松、散步）
     */
    private String selfHelpSuggestion;

    /**
     * 社会支持建议（如向亲友倾诉、加入兴趣社群）
     */
    private String socialSupportSuggestion;

    /**
     * 专业干预建议（如建议寻求心理咨询、精神科就诊评估）
     */
    private String professionalInterveneSuggestion;

    /**
     * 建议优先级：1-自助为主 2-建议寻求支持 3-强烈建议专业干预 4-无法判断
     */
    private Integer suggestionPriority;

}
