package org.lixiyun.server.infrastructure.ai.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI对话配额限流注解
 * <p>标注于需要进行配额扣减的 Service / 异步节点 / 异步 Handler 方法上，由 {@code QuotaLimitAspect}
 * 在方法执行前原子扣减一次配额；扣减成功后才执行业务方法。</p>
 *
 * <p><b>同步 / 异步差异</b>：</p>
 * <ul>
 *   <li><b>同步（默认，TEXT）</b>：{@code userSource=THREAD_LOCAL, async=false} —— 超限抛 1500、资源停用抛 1504，
 *       业务失败自动回滚配额，与主业务逻辑完全隔离。</li>
 *   <li><b>异步节点（TTS / AVATAR_2D）</b>：{@code userSource=OUTPUT_CONTEXT, async=true} —— 从 {@code OutputContext}
 *       取 userId/conversationId（异步线程无请求上下文），被拒时推送 WebSocket 拒绝事件并跳过节点
 *       （纯模式整节点跳过 / 混合模式注入降级标记后放行）。</li>
 *   <li><b>异步 Handler（ASR）</b>：{@code userSource=ARGUMENT, async=true} —— 从方法参数索引取 userId/conversationId，
 *       被拒时推送 WebSocket 拒绝事件并返回 {@code false}（调用方据此跳过后续业务）。</li>
 * </ul>
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

    /**
     * 用户ID来源
     * <ul>
     *   <li>{@link QuotaUserSource#THREAD_LOCAL}：同步请求上下文（默认，TEXT）</li>
     *   <li>{@link QuotaUserSource#OUTPUT_CONTEXT}：方法第 1 参为 {@code OutputContext}（异步输出节点）</li>
     *   <li>{@link QuotaUserSource#ARGUMENT}：从方法参数索引取 userId / conversationId（异步 Handler）</li>
     * </ul>
     *
     * @return 用户ID来源
     */
    QuotaUserSource userSource() default QuotaUserSource.THREAD_LOCAL;

    /**
     * userSource=ARGUMENT 时：userId 参数索引
     *
     * @return userId 参数索引，默认 -1
     */
    int userIdArgIndex() default -1;

    /**
     * userSource=ARGUMENT 时：conversationId 参数索引（供异步拒绝事件推送）
     *
     * @return conversationId 参数索引，默认 -1
     */
    int conversationIdArgIndex() default -1;

    /**
     * 异步拒绝模式：{@code true} 时不抛异常，改推 WebSocket 拒绝事件并阻止业务执行
     * （异步线程无 HTTP 响应通道）；{@code false}（默认）为同步模式，超限抛业务异常。
     *
     * @return 是否异步拒绝模式
     */
    boolean async() default false;

    /**
     * 用户ID来源枚举
     */
    enum QuotaUserSource {
        /** 同步请求上下文（UserInfoThreadLocalUtil） */
        THREAD_LOCAL,
        /** 输出管道上下文（OutputContext，异步节点） */
        OUTPUT_CONTEXT,
        /** 方法参数（异步 Handler） */
        ARGUMENT
    }
}