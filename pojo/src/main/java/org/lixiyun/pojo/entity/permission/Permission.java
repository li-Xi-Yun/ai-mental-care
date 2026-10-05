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
 * 权限表：存储菜单项及其对应权限标识(Permission)实体类
 *
 * @author lixiyun
 * @since 2025-12-13 22:39:53
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Permission implements Serializable {
    
    private static final long serialVersionUID = -90244549428239081L;

    /** 状态标识：0-正常 */
    public static final Integer STATUS_NORMAL = 0;

    /** 状态标识：1-停用 */
    public static final Integer STATUS_DISABLE = 1;

    /**
     * 权限唯一标识，自增主键
     */
    private Long id;

    /**
     * 权限标识符（如：video:delete），用于权限验证
     */
    private String perms;

    /**
     * 权限名称
     */
    private String name;

    /**
     * 权限分组名称，用于组织和管理权限
     */
    private String groupName;

    /**
     * 状态标识：0-正常，1-停用
     */
    private Integer status;

    /**
     * 备注信息，用于描述权限的用途或特殊说明
     */
    private String remark;

    /**
     * 记录创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 创建人用户ID，用于记录初始操作者（可关联用户表）
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
     * 删除标识：0-正常，1-删除（使用逻辑删除时可用）
     */
    @TableLogic
    private Integer deleted;
}

