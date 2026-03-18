package org.lixiyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
    
/**
 * 会话表(Conversation)实体类
 *
 * @author lixiyun
 * @since 2026-03-15 14:47:13
 */
@Data
@Builder
public class Conversation implements Serializable {
    
    private static final long serialVersionUID = 409226948168857358L;
    
    public static final String CURRENT_ROUND = "currentRound";

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
     * 当前轮次
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

