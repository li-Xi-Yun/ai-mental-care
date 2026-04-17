package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表题目表(ScaleQuestion)实体类
 *
 * @author lixiyun
 * @since 2026-04-15 08:24:17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleQuestion implements Serializable {
    
    private static final long serialVersionUID = -34986265418913598L;

    // 正向计分
    public static final Integer SCORE_TYPE_FORWARD = 1;
    // 反向计分
    public static final Integer SCORE_TYPE_REVERSE = 2;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 量表id
     */
    private Long scaleId;
    
    /**
     * 题目内容
     */
    private String title;
    
    /**
     * 题目显示顺序
     */
    private Integer sort;
    
    /**
     * 计分类型：1=正向计分 2=反向计分
     */
    private Integer scoreType;
    
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

