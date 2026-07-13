package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话表(Conversation)实体类
 *
 * @author lixiyun
 * @since 2026-03-15 14:47:13
 */
@Data
@Builder
@TableName(value = "conversation", autoResultMap = true)
public class Conversation implements Serializable {
    
    private static final long serialVersionUID = 409226948168857358L;
    
    /**
     * 会话的唯一标识符
     */ 
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 用户ID，关联用户表
     */
    private Long userId;
    
    /**
     * 该会话的名称
     */
    private String name;
    
    /**
     * 上下文概括
     */
    private String contextSummary;
    
    /**
     * 最后活跃时间，用于业务展示、排序、统计，用户发消息/AI回复都会更新
     */
    private LocalDateTime lastActiveTime;
    
    /**
     * 会话模式
     */
    private String chatMode;
    
    /**
     * 会话映射表
     */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> sessionMapping;
    
    /**
     * 当前轮次，执行中是当前轮次，执行后是下一轮次
     */
    private Integer currentRound;
    
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