package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表主表(Scale)实体类
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scale implements Serializable {

    private static final long serialVersionUID = -74769592153411870L;

    public static final int STATUS_ENABLE = 1;
    public static final int STATUS_DISABLE = 0;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 量表名称
     */
    private String scaleName;
    
    /**
     * 量表说明、指导语、开头介绍
     */
    private String description;
    
    /**
     * 题目总数
     */
    private Integer questionCount;
    
    /**
     * 分类ID
     */
    private Long scaleCategoryId;
    
    /**
     * 状态：1=启用 0=禁用
     */
    private Integer status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 创建人用户ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedTime;
    
    /**
     * 最后更新人用户ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    
    /**
     * 是否删除，0-否，1-是
     */
    @TableLogic
    private Integer deleted;

}
