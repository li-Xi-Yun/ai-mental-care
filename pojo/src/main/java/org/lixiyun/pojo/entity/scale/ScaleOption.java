package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 题目选项表(ScaleOption)实体类
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleOption implements Serializable {
    
    private static final long serialVersionUID = -93012687321730912L;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 所属题目ID
     */
    private Long questionId;
    
    /**
     * 选项描述
     */
    private String optionText;
    
    /**
     * 该选项对应的原始分数
     */
    private Integer score;
    
    /**
     * 选项显示顺序
     */
    private Integer sort;
    
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
