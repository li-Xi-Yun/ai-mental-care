package org.lixiyun.common.authentication.utils.strategy;

import cn.hutool.json.JSONUtil;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.properties.JwtProperties;
import org.lixiyun.common.core.utils.SpringUtils;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.pojo.tool.LoginUser;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JWT认证策略抽象基类
 * 提供通用的JWT处理逻辑
 */
@Slf4j
public abstract class AbstractJwtAuthenticationStrategy implements JwtAuthenticationStrategy {

    protected final JwtProperties jwtProperties;

    protected AbstractJwtAuthenticationStrategy() {
        this.jwtProperties = SpringUtils.getBean(JwtProperties.class);
    }

    @Override
    public String createJwtWithRedis(LoginUser info, boolean flat) {
        return createJwtWithRedis(getSecretKey(), info, getTtl(), flat);
    }

    @Override
    public String createJwtWithRedis(String secretKey, LoginUser info, long ttlMillis, boolean flat) {
        Long userId = info.getBasicsUser().getId();
        Map<String, Object> claims = buildClaims(userId);
        
        String token = createJwtNoTime(secretKey, claims);
        
        if (flat) {
            String redisKey = getRedisKey(userId);
            String jsonStr = JSONUtil.toJsonStr(info);
            RedisUtils.setCacheObject(redisKey, jsonStr, ttlMillis, TimeUnit.SECONDS);
        }
        
        return token;
    }

    @Override
    public void refreshJwtTTL(Long id) {
        String redisKey = getRedisKey(id);
        long ttl = getTtl();
        
        if (RedisUtils.getExpire(redisKey) < ttl / 2) {
            RedisUtils.expire(redisKey, ttl, TimeUnit.SECONDS);
        }
    }

    @Override
    public void refreshJwtTTL(Long id, long extendMillis) {
        String redisKey = getRedisKey(id);
        RedisUtils.expire(redisKey, extendMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void deleteJwt(Long id) {
        String redisKey = getRedisKey(id);
        RedisUtils.deleteObject(redisKey);
    }

    @Override
    public String getUserInfoFromRedis(Long id) {
        return RedisUtils.getCacheObject(getRedisKey(id));
    }

    /**
     * 构建Claims
     * @param userId 用户ID
     * @return Claims Map
     */
    @Override
    public abstract Map<String, Object> buildClaims(Long userId);

    /**
     * 从Claims中获取用户ID
     * @param claims JWT Claims
     * @return 用户ID
     */
    protected abstract Long getUserIdFromClaims(Claims claims);

    /**
     * 解析JWT Token
     * 从Token中提取用户ID
     *
     * @param token JWT Token
     * @return 用户ID
     */
    @Override
    public Long parseJwt(String token) {
        try {
            Claims claims = parseJWT(getSecretKey(), token);
            return getUserIdFromClaims(claims);
        } catch (Exception e) {
            log.debug("{}类型JWT解析失败: {}", this.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 生成jwt
     * 使用Hs256算法, 私匙使用固定秘钥
     *
     * @param secretKey jwt秘钥
     * @param ttlMillis jwt过期时间(毫秒)
     * @param claims    设置的信息
     * @return jwt字符串
     */
    protected static String createJwtWithTime(String secretKey, long ttlMillis, Map<String, Object> claims) {
        // 指定签名的时候使用的签名算法，也就是header那部分
//        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

        // 生成JWT的时间
        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date exp = new Date(expMillis);

        //生成 HMAC 密钥，根据提供的字节数组长度选择适当的 HMAC 算法，并返回相应的 SecretKey 对象。
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        // 设置jwt的body
        JwtBuilder builder = Jwts.builder()
                // 设置签名使用的签名算法和签名使用的秘钥
                .signWith(key)
                // 如果有私有声明，一定要先设置这个自己创建的私有的声明，这个是给builder的claim赋值，一旦写在标准的声明赋值之后，就是覆盖了那些标准的声明的
                .claims(claims)
                // 设置过期时间
                .expiration(exp);
        return builder.compact();
    }

    /**
     * 生成jwt
     * 使用Hs256算法, 私匙使用固定秘钥
     *
     * @param secretKey jwt秘钥
     * @param claims    设置的信息
     * @return jwt字符串
     */
    protected String createJwtNoTime(String secretKey, Map<String, Object> claims) {
        // 指定签名的时候使用的签名算法，也就是header那部分
//        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

        //生成 HMAC 密钥，根据提供的字节数组长度选择适当的 HMAC 算法，并返回相应的 SecretKey 对象。
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        // 设置jwt的body
        return Jwts.builder()
                // 设置签名使用的签名算法和签名使用的秘钥
                .signWith(key)
                // 如果有私有声明，一定要先设置这个自己创建的私有的声明，这个是给builder的claim赋值，一旦写在标准的声明赋值之后，就是覆盖了那些标准的声明的
                .claims(claims)
                .compact();
    }

    /**
     * Token解密
     *
     * @param secretKey jwt秘钥 此秘钥一定要保留好在服务端, 不能暴露出去, 否则sign就可以被伪造, 如果对接多个客户端建议改造成多个
     * @param token     加密后的token
     * @return 解密后的claims
     */
    protected Claims parseJWT(String secretKey, String token) {
        //生成 HMAC 密钥，根据提供的字节数组长度选择适当的 HMAC 算法，并返回相应的 SecretKey 对象。
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        // 得到DefaultJwtParser
        JwtParser jwtParser = Jwts.parser()
                // 设置签名的秘钥
                .verifyWith(key)
                .build();
        
        Jws<Claims> jws = jwtParser.parseSignedClaims(token);
        return jws.getPayload();
    }
}