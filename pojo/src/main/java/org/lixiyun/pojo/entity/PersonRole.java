package org.lixiyun.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户角色关联表
 *
 * @author lixiyun
 * @since 2025-12-13 22:39:54
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PersonRole implements Serializable {
    
    private static final long serialVersionUID = 313557119099461892L;
    
    /**
     * 用户ID
     */
    private Long personId;
    
    /**
     * 角色ID，关联role表的role_id
     */
    private Long roleId;

}

