package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话聊天信息表(ConversationMemory)实体类
 *
 * @author lixiyun
 * @since 2026-03-15 14:47:14
 */
@Data
@Builder
public class ConversationMemory implements Serializable {
    private static final long serialVersionUID = 420266596019691443L;

    /** 未处理状态 */
    public static final int STATE_NOT_PROCESSED = 0;

    /** 已处理状态 */
    public static final int STATE_PROCESSED = 1;

    /**
     * 自增id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 会话id
     */
    private Long conversationId;
    
    /**
     * 消息的具体文本内容
     */
    private String content;
    
    /**
     * 单段语音SER识别的情绪标签（如焦虑、低落、平静、愤怒）
     */
    private String audioEmotionLabel;
    
    /**
     * 语音情绪识别置信度 0-1
     */
    private Double audioEmotionConfidence;
    
    /**
     * 语音声学特征（语速、音量波动、抖动程度、哽咽感等，JSON格式存储）
     */
    private String audioFeature;
    
    /**
     * 消息的类型(USER, ASSISTANT, SYSTEM, TOOL, ASSISTANT_TOOL, THINKING)
     */
    private String type;
    
    /**
     * 消息状态，0-未处理，1-已处理
     */
    private Integer state;
    
    /**
     * 轮次
     */
    private Integer roundNum;
    
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