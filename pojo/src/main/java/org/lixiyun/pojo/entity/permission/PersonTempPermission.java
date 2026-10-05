package org.lixiyun.pojo.entity.permission;

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
 * 用户临时权限表(PersonTempPermission)实体类
 *
 * @author lixiyun
 * @since 2026-07-30
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PersonTempPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 有效有效 */
    public static final int STATUS_VALID = 0;
    /** 手动作废 */
    public static final int STATUS_MANUAL_INVALID = 1;
    /** 已过期 */
    public static final int STATUS_EXPIRED = 2;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long personId;

    /**
     * 权限ID,关联permission表id
     */
    private Long permissionId;

    /**
     * 权限生效时间
     */
    private LocalDateTime startTime;

    /**
     * 权限过期时间,到期自动失效
     */
    private LocalDateTime expireTime;

    /**
     * 0有效 1手动作废 2已过期(可由定时任务更新)
     */
    private Integer status;

    /**
     * 授予该临时权限的原因,审计留存
     */
    private String grantReason;

    /**
     * 操作授予人的用户ID,谁给的权限
     */
    private Long grantUserId;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 更新操作者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 是否有效
     */
    public boolean isValid() {
        return status == STATUS_VALID;
    }

    /**
     * 是否手动作废
     */
    public boolean isManualInvalid() {
        return status == STATUS_MANUAL_INVALID;
    }

    /**
     * 是否已过期
     */
    public boolean isExpired() {
        return status == STATUS_EXPIRED;
    }

}