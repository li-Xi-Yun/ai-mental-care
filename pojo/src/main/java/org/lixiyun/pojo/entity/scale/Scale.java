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
 * 量表主表(scale)实体类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scale implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：禁用 */
    public static final int STATUS_DISABLE = 0;
    /** 状态：启用 */
    public static final int STATUS_ENABLE = 1;

    /** 自增主键ID（一个量表多个版本共用这个id） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类ID */
    private Long scaleCategoryId;

    /** 当前生效的版本ID，关联 scale_version.id */
    private Long currentVersionId;

    /** 量表名称 */
    private String scaleName;

    /** 状态：0=禁用 1=启用 */
    private Integer status;

    /** 是否允许重复作答：0-否 1-是 */
    private Integer allowRepeat;

    /** 重复作答冷却时间(分钟) */
    private Integer coolMinutes;

    /** 作答限时，单位秒，NULL不限时 */
    private Integer timeLimit;

    /** 是否匿名测评：0-否 1-是 */
    private Integer anonymous;

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
     * 判断量表是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }

    /**
     * 判断量表是否已启用
     *
     * @return true表示已启用，false表示已禁用
     */
    public boolean isEnabled() {
        return status != null && status == STATUS_ENABLE;
    }
}