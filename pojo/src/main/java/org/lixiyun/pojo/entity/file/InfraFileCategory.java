package org.lixiyun.pojo.entity.file;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件分类表(InfraFileCategory)实体类
 *
 * @author lixiyun
 * @since 2026-05-01 00:00:00
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InfraFileCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_CATEGORY_NAME = "默认分类";

    /**
     * 默认分类ID，专用于后台管理员使用
     */
    public static final long DEFAULT_CATEGORY_ID = 0L;

    /**
     * 一级分类ID，用于parent_id 字段，0=一级分类
     */
    public static final Long FIRST_LEVEL_CATEGORY = 0L;

    public static final int DEFAULT_TYPE_NO = 0;
    public static final int DEFAULT_TYPE_YES = 1;

    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 父级类目ID，0=一级分类
     */
    private Long parentId;

    /**
     * 人员ID，关联用户/管理员表
     */
    private Long personId;

    /**
     * 类目名称
     */
    private String categoryName;

    /**
     * 是否默认分类：0-否，1-是
     */
    private Integer defaultType;

    /**
     * 该分类下的文件数量
     */
    private Integer fileCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

}