package org.lixiyun.common.authentication.enums;

/**
 * JWT用户类型枚举
 * 用于区分普通用户和管理员用户的Token规则
 */
public enum JwtType {
    
    /**
     * 普通用户
     */
    USER,
    
    /**
     * 管理员用户
     */
    ADMIN
}
