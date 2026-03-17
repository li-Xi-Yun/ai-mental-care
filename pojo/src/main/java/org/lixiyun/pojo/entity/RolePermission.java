package org.lixiyun.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 角色-权限关联表(RolePermission)实体类
 *
 * @author lixiyun
 * @since 2025-12-13 22:39:53
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RolePermission implements Serializable {
    
    private static final long serialVersionUID = 896975197654883538L;
    
    /**
     * 角色ID，逻辑关联role表中的id
     */
    private Long roleId;
    
    /**
     * 菜单权限ID，逻辑关联permissions表中的id
     */
    private Long permissionId;

}

