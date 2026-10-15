package org.lixiyun.server.infrastructure.ai.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.QuotaExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.infrastructure.ai.annotation.QuotaLimit;
import org.lixiyun.server.service.ai.QuotaService;
import org.springframework.stereotype.Component;

/**
 * AI对话配额限流切面
 * <p>拦截所有标注 {@link QuotaLimit} 的 Service 方法，实现「扣减前置、失败回滚」的配额隔离：</p>
 * <ol>
 *   <li>方法执行前调用 {@link QuotaService#tryConsume} 原子扣减一次配额；</li>
 *   <li>扣减被拒（超限）抛 {@link QuotaExceptionEnum#QUOTA_EXCEEDED}；</li>
 *   <li>方法执行成功返回，执行失败（RuntimeException）自动回滚本次扣减。</li>
 * </ol>
 * <p>tryConsume 自身抛出的「资源停用(1504)」在 proceed 之前抛出，不会误触发回滚；只有扣减成功后
 * 业务方法抛异常才回滚（异步 AI 回复阶段失败不退次数，口径见设计方案 §8.1）。</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class QuotaLimitAspect {

    private final QuotaService quotaService;

    /**
     * 环绕通知：扣减 → 执行业务 → 失败回滚
     *
     * @param pjp        切点连接点
     * @param quotaLimit 目标方法上的 {@link QuotaLimit} 注解（由切点绑定，兼容接口/实现类代理）
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("@annotation(quotaLimit)")
    public Object aroundQuotaLimit(ProceedingJoinPoint pjp, QuotaLimit quotaLimit) throws Throwable {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        int resourceType = quotaLimit.resourceType();
        log.debug("[AI配额-切面] 限流校验开始，userId：{}，resourceType：{}", userId, resourceType);

        boolean consumed = quotaService.tryConsume(userId, resourceType);
        if (!consumed) {
            log.info("[AI配额-切面] 用户{}资源{}次数已达上限，拒绝调用", userId, resourceType);
            throw new BusinessException(QuotaExceptionEnum.QUOTA_EXCEEDED.getCode(),
                    quotaService.getRemainingDesc(resourceType));
        }
        try {
            return pjp.proceed();
        } catch (RuntimeException e) {
            // 同步提交阶段失败即时补偿回滚（异步 AI 回复阶段失败不退次数，口径见设计方案 §8.1）
            quotaService.rollbackConsume(userId, resourceType);
            log.warn("[AI配额-切面] 业务执行失败，已回滚配额，userId：{}，resourceType：{}", userId, resourceType);
            throw e;
        }
    }
}