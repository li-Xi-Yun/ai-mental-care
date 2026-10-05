package org.lixiyun.common.authentication.utils.strategy;


import org.lixiyun.pojo.tool.LoginUser;

import java.util.Map;

/**
 * JWT认证策略接口
 * 定义不同用户类型的JWT处理行为
 */
public interface JwtAuthenticationStrategy {

    /**
     * 获取Token名称
     * @return Token在请求头中的名称
     */
    String getTokenName();

    /**
     * 获取密钥
     * @return JWT签名密钥
     */
    String getSecretKey();

    /**
     * 获取TTL（秒）
     * @return JWT有效期
     */
    long getTtl();

    /**
     * 生成JWT并存储到Redis
     * @param info 用户信息
     * @param flat 是否创建Redis中的用户信息
     * @return JWT Token
     */
    String createJwtWithRedis(LoginUser info, boolean flat);

    /**
     * 解析JWT获取用户ID
     * @param token JWT Token
     * @return 用户ID，解析失败返回null
     */
    Long parseJwt(String token);

    /**
     * 从Redis获取用户信息
     * @param id 用户ID
     * @return 用户信息JSON字符串
     */
    String getUserInfoFromRedis(Long id);

    /**
     * 刷新Redis中的JWT TTL
     * @param id 用户ID
     */
    void refreshJwtTTL(Long id);

    /**
     * 刷新Redis中的JWT TTL（指定时间）
     * @param id 用户ID
     * @param extendMillis 延长的毫秒数
     */
    void refreshJwtTTL(Long id, long extendMillis);

    /**
     * 删除Redis中的JWT（登出）
     * @param id 用户ID
     */
    void deleteJwt(Long id);

    /**
     * 获取Redis Key前缀
     * @return Redis Key前缀
     */
    String getRedisKeyPrefix();

    /**
     * 获取完整的Redis Key
     * @param userId 用户ID
     * @return 完整的Redis Key
     */
    default String getRedisKey(Long userId) {
        return getRedisKeyPrefix() + userId;
    }

    /**
     * 构建Claims
     * @param userId 用户ID
     * @return Claims Map
     */
    Map<String, Object> buildClaims(Long userId);

    /**
     * 使用自定义参数创建JWT并存储到Redis
     * @param secretKey 自定义密钥
     * @param info 用户信息
     * @param ttlMillis 自定义有效期（秒）
     * @param flat 是否创建Redis中的用户信息
     * @return JWT Token
     */
    String createJwtWithRedis(String secretKey, LoginUser info, long ttlMillis, boolean flat);
}