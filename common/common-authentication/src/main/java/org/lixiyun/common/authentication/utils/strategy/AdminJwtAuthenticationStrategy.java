package org.lixiyun.common.authentication.utils.strategy;

import io.jsonwebtoken.Claims;
import org.lixiyun.common.authentication.constant.JwtClaimsConstant;

import java.util.Map;

/**
 * 管理员JWT认证策略
 * 处理管理员用户的JWT认证逻辑
 */
public class AdminJwtAuthenticationStrategy extends AbstractJwtAuthenticationStrategy {

    private static final String JWT_REDIS_KEY_PREFIX_ADMIN = "jwt:token:admin:";

    @Override
    public String getTokenName() {
        return jwtProperties.getAdminTokenName();
    }

    @Override
    public String getSecretKey() {
        return jwtProperties.getAdminSecretKey();
    }

    @Override
    public long getTtl() {
        return jwtProperties.getAdminTtl();
    }

    @Override
    public String getRedisKeyPrefix() {
        return JWT_REDIS_KEY_PREFIX_ADMIN;
    }

    @Override
    public Map<String, Object> buildClaims(Long userId) {
        return Map.of(JwtClaimsConstant.EMP_ID, userId.toString());
    }

    @Override
    protected Long getUserIdFromClaims(Claims claims) {
        Object empIdObj = claims.get(JwtClaimsConstant.EMP_ID);
        if (empIdObj != null) {
            return Long.parseLong(empIdObj.toString());
        }
        return null;
    }
}