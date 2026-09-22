package org.lixiyun.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

/**
 * 全局重试日志监听器
 * <p>
 * 为所有标注 {@code @Retryable} 的方法提供重试过程的可观测性。
 * 在每次重试和最终失败时输出 WARN 级别日志，避免重试行为"静默"发生。
 * </p>
 *
 * <p>通过实现 {@link RetryListener} 与 {@code @Component} 注解，
 * 由 Spring Retry 的 {@code RetryConfiguration#afterSingletonsInstantiated} 通过
 * {@code findBeans(RetryListener.class)} 自动发现并注册到所有
 * {@code RetryTemplate} 实例。</p>
 *
 * @author lixiyun
 * @since 2026-09-18
 */
@Slf4j
@Component
public class RetryLoggingListener implements RetryListener {

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        log.warn("[Retry] 第{}次尝试失败，标签：{}，异常：{}",
                context.getRetryCount(),
                getRetryLabel(context),
                throwable.getMessage());
    }

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        if (throwable != null) {
            log.error("[Retry] 重试{}次后仍失败，标签：{}，最终异常：{}", context.getRetryCount(), getRetryLabel(context), throwable.getMessage());
        } else if (context.getRetryCount() > 0) {
            log.info("[Retry] 重试第{}次后成功，标签：{}", context.getRetryCount(), getRetryLabel(context));
        }
    }

    /**
     * 从重试上下文中提取重试标签（{@code @Retryable} 的 {@code label} 属性）
     */
    private static String getRetryLabel(RetryContext context) {
        Object label = context.getAttribute(RetryContext.NAME);
        return label != null ? label.toString() : "未知";
    }
}