package org.lixiyun.pojo.entity.scale;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.constant.DeleteConstant;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表类别表(scale_category)实体类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类别名称 */
    private String categoryName;

    /** 排序 */
    private Integer sort;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 创建人用户ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;

    /** 最后更新人用户ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 是否删除，0-否，1-是 */
    @TableLogic
    private Integer deleted;

    /**
     * 判断类别是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }
}