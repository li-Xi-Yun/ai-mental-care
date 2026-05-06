package org.lixiyun.pojo.entity.permission;

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
    
    /**
     * 权限唯一标识，自增主键
     */
    private Long id;
    
    /**
     * 权限标识符（如：video:delete），用于权限验证
     */
    private String perms;
    
    /**
     * 状态标识：0-正常，1-停用，2-删除（使用逻辑删除时可用）
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
    private Long createdBy;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedTime;
    
    /**
     * 最后更新人用户ID
     */
    private Long updatedBy;

 
}

