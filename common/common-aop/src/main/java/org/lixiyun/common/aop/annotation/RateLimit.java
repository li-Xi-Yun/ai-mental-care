package org.lixiyun.common.aop.annotation;

import lombok.Getter;

import java.lang.annotation.*;
 
/**
 * 分布式限流注解（基于 Redisson 实现）
 * 用于标记需要限制调用频率的方法，防止接口被高频调用或重复提交
 */
@Target(ElementType.METHOD)       // 作用于方法级别
@Retention(RetentionPolicy.RUNTIME)  // 运行时保留，可通过反射获取
@Documented                     // 文档可见
@Inherited                      // 支持子类继承
public @interface RateLimit {

    /**
     * 限流类型
     * 1：接口级限流（默认）
     * 2：用户级限流
     * 3：IP级限流
     */
    RateLimitType type() default RateLimitType.INTERFACE;

    /**
     * 限流标识
     * 用于生成Redis中限流规则的唯一键，建议根据业务场景命名（如"order:submit"）
     */
    String key() default "";

    /**
     * 每个窗口允许的最大请求数，默认为1
     */
    int maxRequests() default 1;

    /**
     * 窗口总大小（毫秒）
     */
    long windowSizeInMillis() default 1000;

    @Getter
    enum RateLimitType {

        INTERFACE(1, "接口级限流"),
        USER(2, "用户级限流"),
        IP(3, "IP级限流");

        private final int code;
        private final String description;

        RateLimitType(int code, String description) {
            this.code = code;
            this.description = description;
        }

    }
}