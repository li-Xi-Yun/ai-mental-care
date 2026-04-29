package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表类别(ScaleCategory)实体类
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleCategory implements Serializable {
    
    private static final long serialVersionUID = -17269189750968680L;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 类别名称
     */
    private String categoryName;

    /**
     * 类别使用数量
     */
    private Integer useCount;
    
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

