package org.lixiyun.common.aop.aspect;

import cn.hutool.core.lang.UUID;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.lixiyun.common.aop.annotation.RateLimit;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.ServletUtils;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;

@Slf4j
@Aspect
@Component
public class CurrentLimitingAspect {

    private final String redisKeyPrefix = "current_limiting:";

    @Autowired
    private ResourceLoader resourceLoader;

    @Pointcut("execution(* org.lixiyun..*controller..*(..))  && @annotation(org.lixiyun.common.aop.annotation.RateLimit)")
    public void point(){}

    @SneakyThrows
    @Before("point() && @annotation(annotation)")
    public void autoFill(JoinPoint joinPoint, RateLimit annotation) {
        log.info("接口限流切面方法开始");
        // 获得方法签名上的注解
        MethodSignature signature = (MethodSignature)joinPoint.getSignature();
        Method method = signature.getMethod();

        RateLimit.RateLimitType type = annotation.type();
        String key = redisKeyPrefix + annotation.key();
        int maxRequests = annotation.maxRequests();
        long windowSizeInMillis = annotation.windowSizeInMillis();


        long currentTimeMillis = System.currentTimeMillis();

        if(type == RateLimit.RateLimitType.USER){
            Long id = UserInfoThreadLocalUtil.getCurrentIdThrow();
            key = key + id + ":" + method.getName();
        } else if(type == RateLimit.RateLimitType.INTERFACE){
            key = key + ":" + method.getName();
        } else if(type == RateLimit.RateLimitType.IP){
            key = key + ":" + ServletUtils.getRequest().getRemoteAddr();
        }

        Resource resource = resourceLoader.getResource("classpath:lua/current-limiting.lua");
        // 修复：使用Resource的输入流读取脚本内容
        String scriptContent = new String(resource.getInputStream().readAllBytes());

        // 创建RedisScript对象
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(scriptContent);
        redisScript.setResultType(Long.class); // 脚本返回1或0，用Long类型接收

        // 执行脚本
        Long result = RedisUtils.execute(redisScript,
                Collections.singletonList(key), // KEYS[1]
                String.valueOf(currentTimeMillis), // ARGV[1] 当前时间戳
                String.valueOf(windowSizeInMillis), // ARGV[2] 窗口时间大小(毫秒)
                String.valueOf(maxRequests), // ARGV[3] 最大请求数
                UUID.randomUUID().toString() // ARGV[4] 唯一标识
        );
        // 结果判断：1允许，0拒绝
        if(result != null && result == 0L){
            throw new BusinessException(SystemExceptionEnum.FREQUENT_OPERATION);
        }

    }

}
