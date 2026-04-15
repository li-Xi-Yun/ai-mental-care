package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Builder;
import lombok.Data;

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
    private Double emotionScore;
    
    /**
     * 情绪变化趋势
     */
    private String emotionTrend;
    
    /**
     * 负向情绪占比（0-1）
     */
    private Double negativeEmotionRatio;
    
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

}

