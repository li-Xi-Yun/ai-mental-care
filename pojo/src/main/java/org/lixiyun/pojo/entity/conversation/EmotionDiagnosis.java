package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;


/**
 * 情感诊断书表（多轮会话汇总）(EmotionDiagnosis)实体类
 *
 * @author lixiyun
 * @since 2026-03-15 14:47:14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "emotion_diagnosis", autoResultMap = true)
public class EmotionDiagnosis implements Serializable {
    private static final long serialVersionUID = 468287925712228372L;

    /** 情绪趋势-上升 */
    public static final int EMOTION_TREND_UP = 0;
    /** 情绪趋势-下降 */
    public static final int EMOTION_TREND_DOWN = 1;
    /** 情绪趋势-平稳 */
    public static final int EMOTION_TREND_STABLE = 2;
    /** 情绪趋势-无法判断 */
    public static final int EMOTION_TREND_UNDETERMINED = 3;

    /** 社会支持水平-良好 */
    public static final int SOCIAL_SUPPORT_GOOD = 0;
    /** 社会支持水平-一般 */
    public static final int SOCIAL_SUPPORT_FAIR = 1;
    /** 社会支持水平-较差 */
    public static final int SOCIAL_SUPPORT_POOR = 2;
    /** 社会支持水平-匮乏 */
    public static final int SOCIAL_SUPPORT_SCARCE = 3;
    /** 社会支持水平-无法判断 */
    public static final int SOCIAL_SUPPORT_UNDETERMINED = 3;

    /** 情绪风险等级-低 */
    public static final int EMOTION_RISK_LOW = 0;
    /** 情绪风险等级-中 */
    public static final int EMOTION_RISK_MEDIUM = 1;
    /** 情绪风险等级-高 */
    public static final int EMOTION_RISK_HIGH = 2;
    /** 情绪风险等级-危急 */
    public static final int EMOTION_RISK_CRITICAL = 3;
    /** 情绪风险等级-无法判断 */
    public static final int EMOTION_RISK_UNDETERMINED = 3;

    /** 是否需要人工干预-否 */
    public static final int NEED_MANUAL_INTERVENE_NO = 0;
    /** 是否需要人工干预-是 */
    public static final int NEED_MANUAL_INTERVENE_YES = 1;

    /** 自伤风险等级-无 */
    public static final int SELF_HARM_RISK_NONE = 0;
    /** 自伤风险等级-低 */
    public static final int SELF_HARM_RISK_LOW = 1;
    /** 自伤风险等级-中 */
    public static final int SELF_HARM_RISK_MEDIUM = 2;
    /** 自伤风险等级-高 */
    public static final int SELF_HARM_RISK_HIGH = 3;
    /** 自伤风险等级-极高 */
    public static final int SELF_HARM_RISK_VERY_HIGH = 4;
    /** 自伤风险等级-无法判断 */
    public static final int SELF_HARM_RISK_UNDETERMINED = 3;

    /** 自杀风险等级-无 */
    public static final int SUICIDE_RISK_NONE = 0;
    /** 自杀风险等级-低 */
    public static final int SUICIDE_RISK_LOW = 1;
    /** 自杀风险等级-中 */
    public static final int SUICIDE_RISK_MEDIUM = 2;
    /** 自杀风险等级-高 */
    public static final int SUICIDE_RISK_HIGH = 3;
    /** 自杀风险等级-极高 */
    public static final int SUICIDE_RISK_VERY_HIGH = 4;
    /** 自杀风险等级-无法判断 */
    public static final int SUICIDE_RISK_UNDETERMINED = 3;

    /** 是否触发危机预警-否 */
    public static final int CRISIS_WARNING_NO = 0;
    /** 是否触发危机预警-是 */
    public static final int CRISIS_WARNING_YES = 1;

    /** 建议优先级-自助为主 */
    public static final int SUGGESTION_PRIORITY_SELF_HELP = 1;
    /** 建议优先级-建议寻求支持 */
    public static final int SUGGESTION_PRIORITY_SEEK_SUPPORT = 2;
    /** 建议优先级-强烈建议专业干预 */
    public static final int SUGGESTION_PRIORITY_PROFESSIONAL = 3;
    /** 建议优先级-无法判断 */
    public static final int SUGGESTION_PRIORITY_UNDETERMINED = 3;

    /** 不认同 */
    public static final int AGREE_NO = 0;
    /** 认同 */
    public static final int AGREE_YES = 1;

    /** 未尝试采纳建议 */
    public static final int USE_SUGGESTION_NONE = 0;
    /** 尝试部分建议 */
    public static final int USE_SUGGESTION_PART = 1;
    /** 全部尝试建议 */
    public static final int USE_SUGGESTION_ALL = 2;

    /**
     * 诊断书主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 会话ID
     */
    private Long conversationId;
    
    /**
     * 用户ID（冗余存储，便于单独查询）
     */
    private Long userId;

    /**
     * 该诊断数据创建或更新时的轮次
     */
    private Integer roundNum;
    
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
     * 核心情绪
     * <br>
     * key:核心情绪标签（多个，如"烦躁,委屈,孤独"）;
     * <br>
     * value:核心情绪置信度（与次要标签一一对应，如"0.85,0.72,0.68"）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, BigDecimal> coreEmotion;

    /**
     * 次要情绪
     * <br>
     * key:次要情绪标签（多个，如"烦躁,委屈,孤独"）;
     * <br>
     * value:次要情绪置信度（与次要标签一一对应，如"0.85,0.72,0.68"）
     */
     @TableField(typeHandler = JacksonTypeHandler.class)
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
     @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, BigDecimal> negativeEmotionDetail;
    
    /**
     * 正向情绪细分占比,如{"开心":0.30,"欣慰":0.15,"放松":0.05}
     */
     @TableField(typeHandler = JacksonTypeHandler.class)
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

    /**
     * 用户对本次诊断打分 1~5分，NULL代表未评分
     */
    private Integer diagnosisScore;

    /**
     * 用户文字反馈、吐槽、补充意见
     */
    private String feedbackContent;

    /**
     * 是否认同风险评估：0-不认同 1-认同 NULL未反馈
     */
    private Integer agreeRiskJudge;

    /**
     * 是否认同给出的自助调节建议：0-不认同 1-认同 NULL未反馈
     */
    private Integer agreeSuggestionSelf;

    /**
     * 是否认同给出的社会支持建议：0-不认同 1-认同 NULL未反馈
     */
    private Integer agreeSuggestionSocial;

    /**
     * 是否认同给出的专业干预建议：0-不认同 1-认同 NULL未反馈
     */
    private Integer agreeSuggestionProfessional;

    /**
     * 是否尝试采纳建议：0-没有 1-尝试部分 2-全部尝试 NULL未反馈
     */
    private Integer useSuggestion;

    /**
     * 用户提交反馈时间
     */
    private LocalDateTime feedbackTime;

    /**
     * 记录创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedTime;
    
    /**
     * 是否删除，0-否，1-是
     */
    @TableLogic
    private Integer deleted;

    /**
     * 将心理诊断结果格式化为LLM提示词可用的文本，仅提取业务关键字段
     * <p>排除：id、conversationId、userId、roundNum、createdTime、updatedTime、deleted 等技术字段，
     * 以及 diagnosisScore、feedbackContent、agree*、useSuggestion、feedbackTime 等用户反馈字段</p>
     *
     * @return 格式化后的提示词文本
     */
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append(buildDiagnosisContentPrompt());
        sb.append(buildCoreEmotionPrompt());
        sb.append(buildEmotionDistributionPrompt());
        sb.append(buildEmotionDynamicsPrompt());
        sb.append(buildTriggerFactorsPrompt());
        sb.append(buildSymptomAssessmentPrompt());
        sb.append(buildSocialFunctionPrompt());
        sb.append(buildPsychologicalResourcesPrompt());
        sb.append(buildRiskAssessmentPrompt());
        sb.append(buildInterventionSuggestionsPrompt());
        return sb.toString();
    }

    // ======================== 评估结果 ========================

    private String buildDiagnosisContentPrompt() {
        if (diagnosisContent == null || diagnosisContent.isEmpty()) {
            return "";
        }
        return "- 诊断内容：" + diagnosisContent + "\n";
    }

    // ======================== 核心情绪结论维度 ========================

    private String buildCoreEmotionPrompt() {
        StringBuilder sb = new StringBuilder();
        if (coreEmotionLabel != null && !coreEmotionLabel.isEmpty()) {
            sb.append("- 核心情绪：").append(coreEmotionLabel);
            if (coreEmotionConfAvg != null) {
                sb.append("（置信度=").append(coreEmotionConfAvg).append("）");
            }
            if (coreEmotionIntensityScore != null) {
                sb.append("（强度=").append(coreEmotionIntensityScore).append("）");
            }
            sb.append("\n");
        }
        if (coreEmotion != null && !coreEmotion.isEmpty()) {
            sb.append("- 核心情绪明细：").append(coreEmotion).append("\n");
        }
        if (secondaryEmotion != null && !secondaryEmotion.isEmpty()) {
            sb.append("- 次要情绪明细：").append(secondaryEmotion).append("\n");
        }
        return sb.toString();
    }

    // ======================== 情绪分布维度 ========================

    private String buildEmotionDistributionPrompt() {
        StringBuilder sb = new StringBuilder();
        if (negativeEmotionRatio != null || neutralEmotionRatio != null || positiveEmotionRatio != null) {
            sb.append("- 情绪占比：负向=").append(negativeEmotionRatio)
                    .append("，中性=").append(neutralEmotionRatio)
                    .append("，正向=").append(positiveEmotionRatio).append("\n");
        }
        if (negativeEmotionDetail != null && !negativeEmotionDetail.isEmpty()) {
            sb.append("- 负向情绪细分：").append(negativeEmotionDetail).append("\n");
        }
        if (positiveEmotionDetail != null && !positiveEmotionDetail.isEmpty()) {
            sb.append("- 正向情绪细分：").append(positiveEmotionDetail).append("\n");
        }
        return sb.toString();
    }

    // ======================== 情绪动态变化维度 ========================

    private String buildEmotionDynamicsPrompt() {
        StringBuilder sb = new StringBuilder();
        if (emotionTrend != null) {
            sb.append("- 情绪趋势：").append(getEmotionTrendDesc()).append("\n");
        }
        if (emotionPeakRound != null || emotionValleyRound != null) {
            sb.append("- 情绪峰值轮次=").append(emotionPeakRound)
                    .append("，低谷轮次=").append(emotionValleyRound).append("\n");
        }
        if (emotionFluctuationAmplitude != null) {
            sb.append("- 情绪波动幅度：").append(emotionFluctuationAmplitude).append("\n");
        }
        if (emotionStableRounds != null) {
            sb.append("- 情绪平稳轮次数：").append(emotionStableRounds).append("\n");
        }
        if (emotionStabilityScore != null) {
            sb.append("- 情绪稳定性得分：").append(emotionStabilityScore).append("\n");
        }
        if (avgP != null || avgA != null || avgD != null) {
            sb.append("- 整体PAD均值：P=").append(avgP)
                    .append("，A=").append(avgA)
                    .append("，D=").append(avgD);
            if (stdP != null || stdA != null || stdD != null) {
                sb.append("，标准差(P=").append(stdP)
                        .append(",A=").append(stdA)
                        .append(",D=").append(stdD).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ======================== 情绪触发因素维度 ========================

    private String buildTriggerFactorsPrompt() {
        StringBuilder sb = new StringBuilder();
        if (coreTriggerScene != null && !coreTriggerScene.isEmpty()) {
            sb.append("- 核心触发场景：").append(coreTriggerScene).append("\n");
        }
        if (coreTriggerKeywords != null && !coreTriggerKeywords.isEmpty()) {
            sb.append("- 核心触发关键词：").append(coreTriggerKeywords).append("\n");
        }
        if (triggerRoundNum != null) {
            sb.append("- 首次触发轮次：").append(triggerRoundNum).append("\n");
        }
        if (firstTriggerDesc != null && !firstTriggerDesc.isEmpty()) {
            sb.append("- 首次触发描述：").append(firstTriggerDesc).append("\n");
        }
        return sb.toString();
    }

    // ======================== 症状与心理状态评估 ========================

    private String buildSymptomAssessmentPrompt() {
        StringBuilder sb = new StringBuilder();
        if (psychologicalState != null && !psychologicalState.isEmpty()) {
            sb.append("- 心理状态评估：").append(psychologicalState).append("\n");
        }
        if (symptomSummary != null && !symptomSummary.isEmpty()) {
            sb.append("- 症状总结：").append(symptomSummary).append("\n");
        }
        if (symptomTags != null && !symptomTags.isEmpty()) {
            sb.append("- 症状标签：").append(symptomTags).append("\n");
        }
        if (symptomDuration != null && !symptomDuration.isEmpty()) {
            sb.append("- 症状持续时长：").append(symptomDuration).append("\n");
        }
        if (onsetPattern != null && !onsetPattern.isEmpty()) {
            sb.append("- 发作模式：").append(onsetPattern).append("\n");
        }
        return sb.toString();
    }

    // ======================== 社会功能与生活影响 ========================

    private String buildSocialFunctionPrompt() {
        StringBuilder sb = new StringBuilder();
        if (socialFunctionImpact != null && !socialFunctionImpact.isEmpty()) {
            sb.append("- 社会功能影响：").append(socialFunctionImpact).append("\n");
        }
        if (impactDomains != null && !impactDomains.isEmpty()) {
            sb.append("- 受影响领域：").append(impactDomains).append("\n");
        }
        if (dailyLifeInfluence != null && !dailyLifeInfluence.isEmpty()) {
            sb.append("- 日常生活影响：").append(dailyLifeInfluence).append("\n");
        }
        return sb.toString();
    }

    // ======================== 心理资源与应对特征 ========================

    private String buildPsychologicalResourcesPrompt() {
        StringBuilder sb = new StringBuilder();
        if (socialSupportLevel != null) {
            sb.append("- 社会支持：").append(getSocialSupportDesc()).append("\n");
        }
        if (protectiveFactors != null && !protectiveFactors.isEmpty()) {
            sb.append("- 保护因素：").append(protectiveFactors).append("\n");
        }
        if (copingStyle != null && !copingStyle.isEmpty()) {
            sb.append("- 应对方式：").append(copingStyle).append("\n");
        }
        return sb.toString();
    }

    // ======================== 风险评估 ========================

    private String buildRiskAssessmentPrompt() {
        StringBuilder sb = new StringBuilder();
        if (emotionRiskLevel != null) {
            sb.append("- 情绪风险等级：").append(getRiskLevelDesc()).append("\n");
        }
        if (selfHarmRiskLevel != null) {
            sb.append("- 自伤风险等级：").append(getSelfHarmRiskDesc()).append("\n");
        }
        if (suicideRiskLevel != null) {
            sb.append("- 自杀风险等级：").append(getSuicideRiskDesc()).append("\n");
        }
        if (riskDetail != null && !riskDetail.isEmpty()) {
            sb.append("- 风险细节：").append(riskDetail).append("\n");
        }
        if (needManualIntervene != null) {
            sb.append("- 需人工干预：").append(needManualIntervene == NEED_MANUAL_INTERVENE_YES ? "是" : "否").append("\n");
        }
        if (emotionAdjustSuggestion != null && !emotionAdjustSuggestion.isEmpty()) {
            sb.append("- 情绪调节建议：").append(emotionAdjustSuggestion).append("\n");
        }
        return sb.toString();
    }

    // ======================== 干预建议 ========================

    private String buildInterventionSuggestionsPrompt() {
        StringBuilder sb = new StringBuilder();
        if (crisisWarning != null) {
            sb.append("- 危机预警：").append(crisisWarning == CRISIS_WARNING_YES ? "已触发" : "未触发").append("\n");
        }
        if (suggestionPriority != null) {
            sb.append("- 建议优先级：").append(getSuggestionPriorityDesc()).append("\n");
        }
        if (selfHelpSuggestion != null && !selfHelpSuggestion.isEmpty()) {
            sb.append("- 自助建议：").append(selfHelpSuggestion).append("\n");
        }
        if (socialSupportSuggestion != null && !socialSupportSuggestion.isEmpty()) {
            sb.append("- 社会支持建议：").append(socialSupportSuggestion).append("\n");
        }
        if (professionalInterveneSuggestion != null && !professionalInterveneSuggestion.isEmpty()) {
            sb.append("- 专业干预建议：").append(professionalInterveneSuggestion).append("\n");
        }
        return sb.toString();
    }

    // ======================== 编码转描述辅助方法 ========================

    private String getEmotionTrendDesc() {
        if (emotionTrend == null) {
            return "未知";
        }
        switch (emotionTrend) {
            case EMOTION_TREND_UP: return "上升";
            case EMOTION_TREND_DOWN: return "下降";
            case EMOTION_TREND_STABLE: return "平稳";
            default: return "无法判断";
        }
    }

    private String getRiskLevelDesc() {
        if (emotionRiskLevel == null) {
            return "未知";
        }
        switch (emotionRiskLevel) {
            case EMOTION_RISK_LOW: return "低";
            case EMOTION_RISK_MEDIUM: return "中";
            case EMOTION_RISK_HIGH: return "高";
            case EMOTION_RISK_CRITICAL: return "危急";
            default: return "无法判断";
        }
    }

    private String getSelfHarmRiskDesc() {
        if (selfHarmRiskLevel == null) {
            return "未知";
        }
        switch (selfHarmRiskLevel) {
            case SELF_HARM_RISK_NONE: return "无";
            case SELF_HARM_RISK_LOW: return "低";
            case SELF_HARM_RISK_MEDIUM: return "中";
            case SELF_HARM_RISK_HIGH: return "高";
            case SELF_HARM_RISK_VERY_HIGH: return "极高";
            default: return "无法判断";
        }
    }

    private String getSuicideRiskDesc() {
        if (suicideRiskLevel == null) {
            return "未知";
        }
        switch (suicideRiskLevel) {
            case SUICIDE_RISK_NONE: return "无";
            case SUICIDE_RISK_LOW: return "低";
            case SUICIDE_RISK_MEDIUM: return "中";
            case SUICIDE_RISK_HIGH: return "高";
            case SUICIDE_RISK_VERY_HIGH: return "极高";
            default: return "无法判断";
        }
    }

    private String getSocialSupportDesc() {
        if (socialSupportLevel == null) {
            return "未知";
        }
        switch (socialSupportLevel) {
            case SOCIAL_SUPPORT_GOOD: return "良好";
            case SOCIAL_SUPPORT_FAIR: return "一般";
            case SOCIAL_SUPPORT_POOR: return "较差";
            case SOCIAL_SUPPORT_SCARCE: return "匮乏";
            default: return "无法判断";
        }
    }

    private String getSuggestionPriorityDesc() {
        if (suggestionPriority == null) {
            return "未知";
        }
        switch (suggestionPriority) {
            case SUGGESTION_PRIORITY_SELF_HELP: return "自助为主";
            case SUGGESTION_PRIORITY_SEEK_SUPPORT: return "建议寻求支持";
            case SUGGESTION_PRIORITY_PROFESSIONAL: return "强烈建议专业干预";
            default: return "无法判断";
        }
    }

}