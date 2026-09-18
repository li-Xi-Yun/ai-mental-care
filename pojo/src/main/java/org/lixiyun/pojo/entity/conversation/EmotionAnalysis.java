package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 情绪记录表(EmotionAnalysis)实体类
 *
 * @author lixiyun
 * @since 2026-03-15 14:47:14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionAnalysis implements Serializable {

    private static final long serialVersionUID = -35884017703413638L;
    
    /**
     * 自增id
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 对应的轮次，与会话ID结合查询对应的对话信息
     */
    private Integer roundNum;
    
    /**
     * 情感分析详情
     */
    private String analysisContent;
    
    /**
     * 情感标签（如anger/开心/neutral/不满等）
     */
    private String emotionLabel;
    
    /**
     * 情感细分标签（如愤怒可细分“不满/暴怒/抱怨”）
     */
    private String emotionSubLabel;
    
    /**
     * 情感识别置信度（0-1，如0.9200）
     */
    private Double emotionConfidence;
    
    /**
     * 情绪本身的强烈程度（0-1，如0.9200）
     */
    private Double emotionIntensity;
    
    /**
     * 较上一轮的情绪变化趋势
     */
    private String emotionTrend;
    
    /**
     * PAD愉悦度P，取值范围[-1,1]
     */
    private Double pScore;
    
    /**
     * PAD唤醒度A，取值范围[-1,1]
     */
    private Double aScore;
    
    /**
     * PAD支配度D，取值范围[-1,1]
     */
    private Double dScore;
    
    /**
     * 负向情绪占比（0-1）
     */
    private Double negativeEmotionRatio;
    
    /**
     * 中性情绪占比（0-1）
     */
    private Double neutralEmotionRatio;
    
    /**
     * 正向情绪占比（0-1）
     */
    private Double positiveEmotionRatio;
    
    /**
     * 记录创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 是否删除，0-否，1-是
     */
    @TableLogic
    private Integer deleted;

    /**
     * 将情绪分析结果格式化为LLM提示词可用的文本，仅提取业务关键字段
     *
     * @return 格式化后的提示词文本
     */
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("- 第").append(roundNum).append("轮：");
        sb.append("情绪=").append(emotionLabel);
        if (emotionSubLabel != null && !emotionSubLabel.isEmpty()) {
            sb.append("（").append(emotionSubLabel).append("）");
        }
        sb.append("，置信度=").append(emotionConfidence);
        sb.append("，强度=").append(emotionIntensity);
        if (emotionTrend != null && !emotionTrend.isEmpty()) {
            sb.append("，趋势=").append(emotionTrend);
        }
        if (pScore != null || aScore != null || dScore != null) {
            sb.append("，PAD(P=").append(pScore)
                    .append(",A=").append(aScore)
                    .append(",D=").append(dScore).append(")");
        }
        sb.append("，占比(负向=").append(negativeEmotionRatio)
                .append(",中性=").append(neutralEmotionRatio)
                .append(",正向=").append(positiveEmotionRatio).append(")");
        if (analysisContent != null && !analysisContent.isEmpty()) {
            sb.append("，分析=").append(analysisContent);
        }
        return sb.toString();
    }

}