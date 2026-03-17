package org.lixiyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检查点数据表(GraphCheckpoint)实体类
 *
 * @author lixiyun
 * @since 2026-03-15 14:47:14
 */
@Data
@Builder
public class GraphCheckpoint implements Serializable {
    private static final long serialVersionUID = -68530311004080644L;
    
    /**
     * 主键ID
     */
    @TableId
    private String checkpointId;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 存储当前Node的标记
     */
    private String nodeId;
    
    /**
     * 存储下一个Node的标记
     */
    private String nextNodeId;
    
    /**
     * 存储state中的数据信息
     */
    private Object stateData;
    
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

