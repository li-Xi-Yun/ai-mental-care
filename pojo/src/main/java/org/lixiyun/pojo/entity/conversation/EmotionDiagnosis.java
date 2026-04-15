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
     * 核心情绪强度（轻度/中度/重度/极重度）
     */
    private String coreEmotionIntensity;

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
     * 整体情绪趋势（上升/下降/平稳/波动）
     */
    private String emotionTrend;
    
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
     * 情绪风险等级（low/medium/high/critical，低/中/高/危急）
     */
    private String emotionRiskLevel;
    
    /**
     * 情绪调节建议（自然语言，如"建议适当休息，减少加班频率"）
     */
    private String emotionAdjustSuggestion;
    
    /**
     * 是否需要人工干预（0-否，1-是）
     */
    private Integer needManualIntervene;
    
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

}

