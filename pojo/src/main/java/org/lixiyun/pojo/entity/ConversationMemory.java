package org.lixiyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
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
    
    /**
     * 自增id
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 会话的唯一标识符
     */
    private Long conversationId;
    
    /**
     * 消息的具体文本内容
     */
    private String content;
    
    /**
     * 消息的类型(USER, ASSISTANT, SYSTEM, TOOL, ASSISTANT_TOOL)
     */
    private String type;
    
    /**
     * 轮次
     */
    private Integer roundNum;
    
    /**
     * 记录创建时间
     */
    private LocalDateTime createdTime;

    /**
         * 创建人用户ID
         */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long createdBy;
    
    /**
     * 是否删除，0-否，1-是
     */
    @TableLogic
    private Integer deleted;
    

}

