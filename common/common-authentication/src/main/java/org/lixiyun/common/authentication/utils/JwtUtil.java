package org.lixiyun.common.authentication.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.constant.JwtClaimsConstant;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.properties.JwtProperties;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.ServletUtils;
import org.lixiyun.common.core.utils.SessionUtil;
import org.lixiyun.common.core.utils.SpringUtils;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.pojo.tool.LoginUser;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtUtil {

    private static final JwtProperties jwtProperties = SpringUtils.getBean(JwtProperties.class);


    // 设置 Redis Key 的前缀
    private static final String JWT_REDIS_KEY_PREFIX = "jwt:token:";
    private static final String JWT_REDIS_KEY_PREFIX_ADMIN = "jwt:token:admin:";
    private static final String JWT_REDIS_KEY_PREFIX_USER = "jwt:token:user:";


    /**
     * 从 Redis 中获取用户信息
     *
     * @param id       人员 ID
     * @param jwtType  JWT类型 {@link JwtType}
     * @return 用户信息字符串
     */
    public static String getUserInfoFromRedis(Long id, JwtType jwtType) {
        if (jwtType == JwtType.USER) {
            return RedisUtils.getCacheObject(JWT_REDIS_KEY_PREFIX_USER + id);
        } else if (jwtType == JwtType.ADMIN) {
            return RedisUtils.getCacheObject(JWT_REDIS_KEY_PREFIX_ADMIN + id);
        }
        return null;
    }

    /**
     * 获取Token
     * <p>优先从请求头中提取 Token，如果同时存在多个 token，记录警告日志</p>
     *
     * @param request HttpServletRequest 请求对象
     * @return Token信息，如果都不存在则返回 null
     */
    public static String getToken(HttpServletRequest request) {
        String userToken = request.getHeader(jwtProperties.getUserTokenName());
        String adminToken = request.getHeader(jwtProperties.getAdminTokenName());
        
        // 如果两个 token 都存在，记录警告日志
        if (StrUtil.isNotBlank(userToken) && StrUtil.isNotBlank(adminToken)) {
            log.warn("请求中同时存在用户token和管理员token，将优先使用用户token。建议前端检查是否重复传递token");
            return userToken;
        }
        
        // 优先返回用户token，其次返回管理员token
        if (StrUtil.isNotBlank(userToken)) {
            return userToken;
        }
        
        return adminToken;
    }

    /**
     * 一次性解析 JWT，返回类型、用户 ID 和 Claims 信息
     *
     * @param token JWT Token
     * @param jwtType jwt类型
     * @return 登录人员的ID
     */
    public static Long parseJwtWithType(String token, JwtType jwtType) {
        if(jwtType == JwtType.USER){
            try {
                String secretKey = jwtProperties.getUserSecretKey();
                Claims claims = parseJWT(secretKey, token);
                Object userIdObj = claims.get(JwtClaimsConstant.USER_ID);
                if (userIdObj != null) {
                    return Long.parseLong(userIdObj.toString());
                }
            } catch (Exception e) {
                log.debug("USER类型JWT解析失败: {}", e.getMessage());
                return null;
            }
        } else if(jwtType == JwtType.ADMIN){
            try {
                String secretKey = jwtProperties.getAdminSecretKey();
                Claims claims = parseJWT(secretKey, token);
                Object empIdObj = claims.get(JwtClaimsConstant.EMP_ID);
                if (empIdObj != null) {
                    return Long.parseLong(empIdObj.toString());
                }
            } catch (Exception e) {
                log.debug("ADMIN类型JWT解析失败: {}", e.getMessage());
                return null;
            }
        } else{
            log.warn("无法识别JWT类型或解析失败");
            return null;
        }
        return null;
    }

    /**
     * 生成 JWT 并将用户信息存储到 Redis，支持动态刷新 TTL
     *
     * @param info    用户信息 {@link LoginUser}
     * @param flat    是否创建对应的 Redis 中的用户信息
     * @param jwtType JWT类型 {@link JwtType}
     * @return 生成的 JWT Token
     */
    public static String createJwtWithRedis(LoginUser info, boolean flat, JwtType jwtType) {
        String secretKey = jwtType == JwtType.USER ? jwtProperties.getUserSecretKey() : jwtProperties.getAdminSecretKey();
        long ttlMillis = jwtType == JwtType.USER ? jwtProperties.getUserTtl() : jwtProperties.getAdminTtl();
        return createJwtWithRedis(secretKey, info, ttlMillis, flat, jwtType);
    }

    /**
     * 生成 JWT 并将用户信息存储到 Redis，支持动态刷新 TTL
     *
     * @param secretKey   签名密钥
     * @param info        用户信息 {@link LoginUser}
     * @param ttlMillis   JWT 初始有效期（秒）
     * @param flat        是否创建对应的redis中的用户信息
     * @param jwtType     JWT类型 {@link JwtType}
     * @return            生成的 JWT Token
     */
    public static String createJwtWithRedis(String secretKey, LoginUser info, long ttlMillis, boolean flat, JwtType jwtType) {
        // 生成 JWT
        Long userId = info.getBasicsUser().getId();
        Map<String, Object> claims;
        if (jwtType == JwtType.USER) {
            claims = Map.of(JwtClaimsConstant.USER_ID, userId.toString());
        } else if (jwtType == JwtType.ADMIN) {
            claims = Map.of(JwtClaimsConstant.EMP_ID, userId.toString());
        } else {
            throw new BusinessException(AuthenticationExceptionEnum.JWT_ERROR);
        }

        String token = createJwtNoTime(secretKey, claims);
        if (flat) {
            // 构建 Redis Key
            String redisKey = jwtType == JwtType.USER ? JWT_REDIS_KEY_PREFIX_USER + userId : JWT_REDIS_KEY_PREFIX_ADMIN + userId;

            // 将用户信息存储到 Redis 中，绑定 JWT 的有效期
            String jsonStr = JSONUtil.toJsonStr(info);
            RedisUtils.setCacheObject(redisKey, jsonStr, ttlMillis, TimeUnit.SECONDS);
        }

        return token;
    }

    /**
     * 手动刷新指定用户的 JWT TTL，固定刷新时间
     *
     * @param id      人员 ID
     * @param jwtType JWT类型 {@link JwtType}
     */
    public static void refreshJwtTTLWithRedis(Long id, JwtType jwtType) {
        String redisKey = jwtType == JwtType.USER ? JWT_REDIS_KEY_PREFIX_USER + id : JWT_REDIS_KEY_PREFIX_ADMIN + id;
        long ttl = jwtType == JwtType.USER ? jwtProperties.getUserTtl() : jwtProperties.getAdminTtl();

        // 刷新 Redis 中的 TTL（覆盖有效期）
        if (RedisUtils.getExpire(redisKey) < ttl / 2) {
            RedisUtils.expire(redisKey, ttl, TimeUnit.SECONDS);
        }
    }

    /**
     * 手动刷新指定用户的 JWT TTL，指定刷新时间
     *
     * @param id 人员 ID
     * @param extendMillis 延长的毫秒数
     */
    public static void refreshJwtTTLWithRedis(Long id, long extendMillis, JwtType jwtType) {
        if(jwtType == JwtType.USER){
            String redisKey = JWT_REDIS_KEY_PREFIX_USER + id;
            RedisUtils.expire(redisKey, extendMillis, TimeUnit.MILLISECONDS);
        } else if(jwtType == JwtType.ADMIN){
            String redisKey = JWT_REDIS_KEY_PREFIX_ADMIN + id;
            RedisUtils.expire(redisKey, extendMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 删除指定用户存放在Redis中的 JWT（登出）
     *
     * @param id 用户 ID
     */
    public static void deleteJwtWithRedis(Long id, JwtType jwtType) {
        if(jwtType == JwtType.USER){
            String redisKey = JWT_REDIS_KEY_PREFIX_USER + id;
            RedisUtils.deleteObject(redisKey);
        } else if(jwtType == JwtType.ADMIN){
            String redisKey = JWT_REDIS_KEY_PREFIX_ADMIN + id;
            RedisUtils.deleteObject(redisKey);
        }
    }


    // ================================ Session 存储的方法 ======================================
    /**
     * 生成 JWT 并将用户信息存储到 Session，支持动态刷新 TTL
     *
     * @param secretKey   签名密钥
     * @param userId        用户id
     * @param ttlMillis   JWT 初始有效期（秒）
     * @param flat         是否创建对应的 Session 中的用户信息
     * @return            生成的 JWT Token
     */
    public static String createJwtWithSession(String secretKey, Long userId,
                                              long ttlMillis, boolean flat) {
        Map<String, Object> claims = Map.of(JwtClaimsConstant.USER_ID, userId.toString());

        // 生成 JWT
        String token = createJwtNoTime(secretKey, claims);
        if (flat) {
            // 构建 Key（格式：jwt:token:{id}）
            String key = JWT_REDIS_KEY_PREFIX + userId;

            SessionUtil.setExpirableAttribute(key, token, (int) ttlMillis);
        }

        return token;
    }

    public static String createJwtWithSession(Long userId, boolean flat) {
        return createJwtWithSession(jwtProperties.getUserSecretKey(), userId, jwtProperties.getUserTtl(), flat);
    }

    /**
     * 解析 JWT Session专属
     *
     * @param secretKey   签名密钥
     * @param token       JWT Token
     * @return            解析后的 Claims
     * @throws JwtException 如果 Token 无效或签名错误
     */
    public static Claims parseJwtAndRefreshWithSession(String secretKey, String token) {
        // 解析 JWT
        Claims claims = parseJWT(secretKey, token);

        // 从 Claims 中获取用户 ID（需在生成 JWT 时设置），约定为 "userId"
        String userId = (String) claims.get(JwtClaimsConstant.USER_ID);
        if (userId == null) {
            throw new BusinessException(AuthenticationExceptionEnum.JWT_ERROR);
        }

        String key = JWT_REDIS_KEY_PREFIX + userId;
        Object attribute = SessionUtil.getExpirableAttribute(key);
        if (attribute == null) {
            throw new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN);
        }

        refreshJwtTTLWithSession(key, jwtProperties.getUserTtl());

        return claims;
    }

    /**
     * 手动刷新指定用户的 JWT TTL Session 专属
     *
     * @param key 存储在Session中的 key
     * @param extendMillis 延长的毫秒数
     */
    public static void refreshJwtTTLWithSession(String key, long extendMillis) {

        long expirableAttributeRemainingSeconds = SessionUtil.getExpirableAttributeRemainingSeconds(key);

        if(expirableAttributeRemainingSeconds < jwtProperties.getUserTtl() / 2){
            SessionUtil.refreshExpirableAttribute(key, extendMillis);
        }
    }

    /**
     * 删除指定用户存放在Session中的 JWT（登出）
     *
     * @param id 用户 ID
     */
    public static void deleteJwtWithSession(Long id) {
        String key = JWT_REDIS_KEY_PREFIX + id;
        ServletUtils.getSession().removeAttribute(key);
    }


    // =================================== 基础方法 ======================================



    /**
     * 生成jwt
     * 使用Hs256算法, 私匙使用固定秘钥
     *
     * @param secretKey jwt秘钥
     * @param ttlMillis jwt过期时间(毫秒)
     * @param claims    设置的信息
     * @return jwt字符串
     */
    private static String createJwtWithTime(String secretKey, long ttlMillis, Map<String, Object> claims) {
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
    private static String createJwtNoTime(String secretKey, Map<String, Object> claims) {
        // 指定签名的时候使用的签名算法，也就是header那部分
//        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

        //生成 HMAC 密钥，根据提供的字节数组长度选择适当的 HMAC 算法，并返回相应的 SecretKey 对象。
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        // 设置jwt的body
        JwtBuilder builder = Jwts.builder()
                // 设置签名使用的签名算法和签名使用的秘钥
                .signWith(key)
                // 如果有私有声明，一定要先设置这个自己创建的私有的声明，这个是给builder的claim赋值，一旦写在标准的声明赋值之后，就是覆盖了那些标准的声明的
                .claims(claims);
        return builder.compact();
    }

    /**
     * Token解密
     *
     * @param secretKey jwt秘钥 此秘钥一定要保留好在服务端, 不能暴露出去, 否则sign就可以被伪造, 如果对接多个客户端建议改造成多个
     * @param token     加密后的token
     * @return
     */
    private static Claims parseJWT(String secretKey, String token) {
        //生成 HMAC 密钥，根据提供的字节数组长度选择适当的 HMAC 算法，并返回相应的 SecretKey 对象。
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        // 得到DefaultJwtParser
        JwtParser jwtParser = Jwts.parser()
                // 设置签名的秘钥
                .verifyWith(key)
                .build();
        Jws<Claims> jws = jwtParser.parseSignedClaims(token);
        Claims claims = jws.getPayload();
        return claims;
    }
}
