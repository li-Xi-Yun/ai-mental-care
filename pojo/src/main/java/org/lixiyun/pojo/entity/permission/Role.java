package org.lixiyun.pojo.entity.permission;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * 角色表(Role)实体类
 *
 * @author lixiyun
 * @since 2025-12-13 22:39:53
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Role implements Serializable {
    
    private static final long serialVersionUID = 747557300162764533L;

    /** 状态标识：0-正常 */
    public static final Integer STATUS_NORMAL = 0;

    /** 状态标识：1-停用 */
    public static final Integer STATUS_DISABLE = 1;

    /**
     * 角色ID,自增主键
     */
    private Long id;

    /**
     * 角色名(如admin/user/VIP),唯一且非空
     */
    private String roleKey;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色状态（0正常 1停用）
     */
    private Integer status;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 创建人用户id
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 最后更新人用户id
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 删除标识：0-正常，1-删除（使用逻辑删除时可用）
     */
    @TableLogic
    private Integer deleted;
}

