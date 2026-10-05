package org.lixiyun.common.authentication.utils.strategy;

import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT认证策略工厂
 * 根据JwtType获取对应的认证策略
 */
public class JwtAuthenticationStrategyFactory {

    private static final Map<JwtType, JwtAuthenticationStrategy> STRATEGY_MAP = new ConcurrentHashMap<>();

    static {
        STRATEGY_MAP.put(JwtType.USER, new UserJwtAuthenticationStrategy());
        STRATEGY_MAP.put(JwtType.ADMIN, new AdminJwtAuthenticationStrategy());
    }

    /**
     * 获取JWT认证策略
     * @param jwtType JWT类型
     * @return 对应的认证策略
     */
    public static JwtAuthenticationStrategy getStrategy(JwtType jwtType) {
        JwtAuthenticationStrategy strategy = STRATEGY_MAP.get(jwtType);
        if (strategy == null) {
            throw new BusinessException(AuthenticationExceptionEnum.JWT_ERROR);
        }
        return strategy;
    }

    /**
     * 获取所有JWT认证策略
     * @return 所有认证策略列表
     */
    public static List<JwtAuthenticationStrategy> getAllStrategies() {
        return new ArrayList<>(STRATEGY_MAP.values());
    }

    /**
     * 注册自定义策略
     * @param jwtType JWT类型
     * @param strategy 认证策略
     */
    public static void registerStrategy(JwtType jwtType, JwtAuthenticationStrategy strategy) {
        STRATEGY_MAP.put(jwtType, strategy);
    }
}