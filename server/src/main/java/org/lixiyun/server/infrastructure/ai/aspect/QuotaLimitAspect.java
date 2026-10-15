package org.lixiyun.server.infrastructure.ai.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.QuotaExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.quota.QuotaConfig;
import org.lixiyun.server.infrastructure.ai.annotation.QuotaLimit;
import org.lixiyun.server.infrastructure.ai.annotation.QuotaLimit.QuotaUserSource;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputContext;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputDataType;
import org.lixiyun.server.service.ai.QuotaService;
import org.springframework.stereotype.Component;

/**
 * AI对话配额限流切面
 * <p>拦截所有标注 {@link QuotaLimit} 的方法，实现「扣减前置、失败回滚/异步降级」的配额隔离：</p>
 * <ol>
 *   <li><b>同步（THREAD_LOCAL）</b>：扣减成功才 proceed；超限抛 {@link QuotaExceptionEnum#QUOTA_EXCEEDED}（1500）、
 *       资源停用抛 1504；proceed 抛异常自动回滚。TEXT 走此路径，与现状一致。</li>
 *   <li><b>异步节点（OUTPUT_CONTEXT）</b>：userId 从 {@link OutputContext#getUserId()} 取（异步线程无请求上下文）；
 *       被拒推 WebSocket 拒绝事件：纯模式整节点跳过（省 LLM 成本），混合模式向 dataStore 注入跳过标记后放行
 *       （节点只跳过自身副作用，保留 TEXT/TTS 通道）。</li>
 *   <li><b>异步 Handler（ARGUMENT）</b>：userId/conversationId 从方法参数索引取；被拒推 WebSocket 拒绝事件并返回
 *       {@code false}（调用方跳过后续业务，如 ASR 句子提交）。</li>
 * </ol>
 * <p>异步链路 proceed 失败<b>不退次数</b>（口径见设计方案 §8.1「异步 AI 回复阶段失败不退次数」）；同步链路才回滚。</p>
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
    private final ConversationWebSocketManager conversationWebSocketManager;

    /**
     * 环绕通知：扣减 → 执行业务 → 失败回滚/异步降级
     *
     * @param pjp        切点连接点
     * @param quotaLimit 目标方法上的 {@link QuotaLimit} 注解（由切点绑定，兼容接口/实现类代理）
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("@annotation(quotaLimit)")
    public Object aroundQuotaLimit(ProceedingJoinPoint pjp, QuotaLimit quotaLimit) throws Throwable {
        Long userId = resolveUserId(pjp, quotaLimit);
        // 防御兜底：异步节点/Handler 用户上下文缺失时放行，不阻断管道
        if (userId == null) {
            log.warn("[AI配额-切面] 无法解析userId，放行，方法：{}", pjp.getSignature().getName());
            return pjp.proceed();
        }

        int resourceType = quotaLimit.resourceType();
        Long conversationId = resolveConversationId(pjp, quotaLimit);
        log.debug("[AI配额-切面] 限流校验开始，userId：{}，resourceType：{}", userId, resourceType);

        boolean consumed;
        try {
            consumed = quotaService.tryConsume(userId, resourceType);
        } catch (BusinessException e) {
            // 资源停用(1504)：同步抛异常，异步推拒绝事件并阻止业务执行
            if (quotaLimit.async()) {
                conversationWebSocketManager.sendQuotaRejected(userId, conversationId, resourceType);
                return rejectResult(pjp, quotaLimit);
            }
            throw e;
        }
        if (!consumed) {
            if (quotaLimit.async()) {
                conversationWebSocketManager.sendQuotaRejected(userId, conversationId, resourceType);
                return rejectResult(pjp, quotaLimit);
            }
            log.info("[AI配额-切面] 用户{}资源{}次数已达上限，拒绝调用", userId, resourceType);
            throw new BusinessException(QuotaExceptionEnum.QUOTA_EXCEEDED.getCode(),
                    quotaService.getRemainingDesc(resourceType));
        }

        try {
            return pjp.proceed();
        } catch (RuntimeException e) {
            if (quotaLimit.async()) {
                // 异步链路失败不退次数（口径 §8.1），仅告警
                log.warn("[AI配额-切面] 异步业务执行失败（不退次数），userId：{}，resourceType：{}", userId, resourceType);
            } else {
                // 同步提交阶段失败即时补偿回滚
                quotaService.rollbackConsume(userId, resourceType);
                log.warn("[AI配额-切面] 业务执行失败，已回滚配额，userId：{}，resourceType：{}", userId, resourceType);
            }
            throw e;
        }
    }

    /**
     * 异步拒绝结果：OUTPUT_CONTEXT 节点 → 纯模式跳过整节点 / 混合模式注入降级标记后放行；
     * ARGUMENT Handler → 返回 {@code false}，调用方据此跳过后续业务。
     *
     * @param pjp        切点连接点
     * @param quotaLimit 注解（含 userSource）
     * @return 被拦截方法的返回结果
     * @throws Throwable 混合模式放行节点时节点执行抛出的异常
     */
    private Object rejectResult(ProceedingJoinPoint pjp, QuotaLimit quotaLimit) throws Throwable {
        if (quotaLimit.userSource() == QuotaUserSource.OUTPUT_CONTEXT) {
            OutputContext context = (OutputContext) pjp.getArgs()[0];
            OutputDataType served = mapResourceToOutputType(quotaLimit.resourceType());
            if (context.onlyContainOutputType(served)) {
                log.info("[AI配额-切面] 纯{}模式，跳过本轮节点，userId：{}", served, context.getUserId());
                return null;   // 跳过整个节点：不订阅 → 本轮无回复，仅拒绝事件（省 LLM 成本）
            }
            // 混合模式：注入跳过标记后放行，节点仅跳过自身副作用（保留 TEXT/TTS 通道）
            context.putData("quota:skip:" + quotaLimit.resourceType(), Boolean.TRUE);
            try {
                return pjp.proceed();
            } catch (RuntimeException e) {
                log.warn("[AI配额-切面] 混合模式节点执行失败，userId：{}", context.getUserId());
                throw e;
            }
        }
        return Boolean.FALSE;   // ARGUMENT（ASR Handler）：被拒 → 返回 false
    }

    /**
     * 资源类型 → 节点负责的输出类型（节点降级/纯模式判断用）
     *
     * @param resourceType 资源类型
     * @return 输出数据类型；非输出节点资源返回 {@code null}
     */
    private OutputDataType mapResourceToOutputType(int resourceType) {
        return switch (resourceType) {
            case QuotaConfig.RESOURCE_TYPE_TTS -> OutputDataType.AUDIO;
            case QuotaConfig.RESOURCE_TYPE_AVATAR_2D -> OutputDataType.AVATAR_2D;
            default -> null;
        };
    }

    /**
     * 解析当前操作用户ID
     *
     * @param pjp        切点连接点
     * @param quotaLimit 注解（含 userSource）
     * @return 用户ID；无法解析返回 {@code null}
     */
    private Long resolveUserId(ProceedingJoinPoint pjp, QuotaLimit quotaLimit) {
        return switch (quotaLimit.userSource()) {
            case OUTPUT_CONTEXT -> {
                OutputContext context = (OutputContext) pjp.getArgs()[0];
                yield context.getUserId();
            }
            case ARGUMENT -> (Long) pjp.getArgs()[quotaLimit.userIdArgIndex()];
            default -> UserInfoThreadLocalUtil.getCurrentIdThrow();
        };
    }

    /**
     * 解析当前会话ID（仅异步拒绝事件推送使用；THREAD_LOCAL 同步模式返回 {@code null}）
     *
     * @param pjp        切点连接点
     * @param quotaLimit 注解（含 userSource）
     * @return 会话ID；不可得返回 {@code null}
     */
    private Long resolveConversationId(ProceedingJoinPoint pjp, QuotaLimit quotaLimit) {
        return switch (quotaLimit.userSource()) {
            case OUTPUT_CONTEXT -> ((OutputContext) pjp.getArgs()[0]).getConversationId();
            case ARGUMENT -> (Long) pjp.getArgs()[quotaLimit.conversationIdArgIndex()];
            default -> null;
        };
    }
}