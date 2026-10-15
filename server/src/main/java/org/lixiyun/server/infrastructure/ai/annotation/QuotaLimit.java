package org.lixiyun.server.infrastructure.ai.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI对话配额限流注解
 * <p>标注于需要进行配额扣减的 Service 方法上，由 {@code QuotaLimitAspect} 在方法执行前
 * 原子扣减一次配额；扣减成功后才执行业务方法，业务方法抛异常时自动回滚本次扣减。</p>
 * <p>与主业务逻辑完全隔离：方法体内无需任何配额代码。</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QuotaLimit {

    /**
     * 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D
     * <p>见 {@link org.lixiyun.pojo.entity.quota.QuotaConfig#RESOURCE_TYPE_TEXT} 等常量。</p>
     *
     * @return 资源类型
     */
    int resourceType();
}