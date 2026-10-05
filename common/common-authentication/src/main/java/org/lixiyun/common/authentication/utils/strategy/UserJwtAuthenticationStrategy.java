package org.lixiyun.common.authentication.utils.strategy;

import io.jsonwebtoken.Claims;
import org.lixiyun.common.authentication.constant.JwtClaimsConstant;

import java.util.Map;

/**
 * 用户JWT认证策略
 * 处理普通用户的JWT认证逻辑
 */
public class UserJwtAuthenticationStrategy extends AbstractJwtAuthenticationStrategy {

    private static final String JWT_REDIS_KEY_PREFIX_USER = "jwt:token:user:";

    @Override
    public String getTokenName() {
        return jwtProperties.getUserTokenName();
    }

    @Override
    public String getSecretKey() {
        return jwtProperties.getUserSecretKey();
    }

    @Override
    public long getTtl() {
        return jwtProperties.getUserTtl();
    }

    @Override
    public String getRedisKeyPrefix() {
        return JWT_REDIS_KEY_PREFIX_USER;
    }

    @Override
    public Map<String, Object> buildClaims(Long userId) {
        return Map.of(JwtClaimsConstant.USER_ID, userId.toString());
    }

    @Override
    protected Long getUserIdFromClaims(Claims claims) {
        Object userIdObj = claims.get(JwtClaimsConstant.USER_ID);
        if (userIdObj != null) {
            return Long.parseLong(userIdObj.toString());
        }
        return null;
    }
}