package org.lixiyun.server.infrastructure.log.aspect;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.log.AiFlowExecution;
import org.lixiyun.server.infrastructure.log.FlowExecutionContextManager;
import org.lixiyun.server.infrastructure.conversation.ConversationRepository;
import org.lixiyun.server.mapper.AiFlowExecutionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * AI主流程执行记录切面
 * <p>拦截 {@code ConversationMessageProcessor.processConversationMessage(Long)} 方法，
 * 在流程入口INSERT一条ai_flow_execution记录，在finally中UPDATE该记录的状态、耗时等信息。</p>
 *
 * <h3>执行流程</h3>
 * <pre>
 * @Around 拦截 processConversationMessage(conversationId)
 * │
 * ├── 【入口 — INSERT】
 * │   ├── trace_id = IdUtil.getSnowflake(23, 17).nextId()
 * │   ├── started_at = LocalDateTime.now()
 * │   ├── FlowExecutionContextManager.initContext(conversationId, traceId)
 * │   └── INSERT ai_flow_execution (trace_id, conversation_id, flow_type=1, status=1, started_at)
 * │
 * ├── 【执行原方法】pjp.proceed()
 * │
 * └── 【finally — UPDATE】
 *     ├── finished_at, duration_ms, status, round_num, user_id, error_code, error_message
 *     └── FlowExecutionContextManager.removeContext(conversationId)
 * </pre>
 *
 * @author lixiyun
 * @since 2026-09-11
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class FlowExecutionAspect {

    /**
     * 流程执行上下文管理器，负责初始化/移除/查询流程上下文，以及递增节点序号
     */
    private final FlowExecutionContextManager flowExecutionContextManager;
    /**
     * AI流程执行记录 Mapper，用于 INSERT / UPDATE ai_flow_execution 表
     */
    private final AiFlowExecutionMapper aiFlowExecutionMapper;
    /**
     * 会话仓储，用于在流程结束时反查 Conversation 获取 roundNum 和 userId
     */
    private final ConversationRepository conversationRepository;

    /**
     * 切点：拦截 {@code ConversationMessageProcessor.processConversationMessage(Long)} 方法，
     * 即 AI 主流程的入口方法
     */
    @Pointcut("execution(* org.lixiyun.server.infrastructure.conversation.ConversationMessageProcessor.processConversationMessage(Long))")
    public void processConversationMessagePointcut() {
    }

    /**
     * 环绕通知：在 AI 主流程入口 INSERT 一条执行记录，流程结束后 UPDATE 状态、耗时等信息
     * <ol>
     *   <li>从方法参数获取 conversationId，生成雪花 traceId，记录 startedAt</li>
     *   <li>调用 {@link FlowExecutionContextManager#initContext} 初始化上下文</li>
     *   <li>INSERT ai_flow_execution 记录（状态=RUNNING）</li>
     *   <li>执行原方法 {@code pjp.proceed()}</li>
     *   <li>finally 中 UPDATE 记录为 COMPLETED/FAILED，并移除上下文</li>
     * </ol>
     *
     * @param pjp 切点连接点，第一个参数为 conversationId
     * @return 原方法的返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("processConversationMessagePointcut()")
    public Object aroundProcessConversationMessage(ProceedingJoinPoint pjp) throws Throwable {
        Long conversationId = (Long) pjp.getArgs()[0];
        long traceId = IdUtil.getSnowflake(23, 17).nextId();
        LocalDateTime startedAt = LocalDateTime.now();


        Integer roundNum = null;
        Long userId = null;
        try {
            Conversation conversation = conversationRepository.getConversationById(conversationId);
            if (conversation != null) {
                roundNum = conversation.getCurrentRound();
                userId = conversation.getUserId();
                log.debug("反查Conversation成功, conversationId={}, roundNum={}, userId={}", conversationId, roundNum, userId);
                flowExecutionContextManager.initContext(conversationId, traceId, roundNum, userId);
            }
        } catch (Exception e) {
            log.error("反查Conversation失败, conversationId={}", conversationId, e);
            throw new RuntimeException("反查Conversation失败", e);
        }

        AiFlowExecution flowExecution = AiFlowExecution.builder()
                .traceId(traceId)
                .conversationId(conversationId)
                .roundNum(roundNum)
                .userId(userId)
                .flowType(AiFlowExecution.FLOW_TYPE_CHAT)
                .status(AiFlowExecution.STATUS_RUNNING)
                .startedAt(startedAt)
                .build();
        aiFlowExecutionMapper.insert(flowExecution);
        log.debug("INSERT ai_flow_execution, traceId={}, conversationId={}", traceId, conversationId);

        Throwable caughtException = null;
        Object result = null;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            caughtException = t;
            throw t;
        } finally {
            try {
                updateFlowExecutionOnFinish(traceId, conversationId, startedAt, caughtException);
            } catch (Exception e) {
                log.error("UPDATE ai_flow_execution 失败, traceId={}, conversationId={}", traceId, conversationId, e);
            }
            try {
                flowExecutionContextManager.removeContext(conversationId);
            } catch (Exception e) {
                log.error("removeContext 失败, conversationId={}", conversationId, e);
            }
        }

        return result;
    }

    /**
     * 流程结束时更新 ai_flow_execution 记录
     * <p>反查 Conversation 获取 roundNum 和 userId，计算耗时，根据是否有异常设置状态为 COMPLETED 或 FAILED，
     * 异常信息截取前 2000 字符存入 error_message。</p>
     *
     * @param traceId        雪花 ID，作为更新条件
     * @param conversationId 会话 ID，用于反查 Conversation
     * @param startedAt      流程开始时间，用于计算 durationMs
     * @param exception      原方法抛出的异常，为 null 表示成功
     */
    private void updateFlowExecutionOnFinish(Long traceId, Long conversationId, LocalDateTime startedAt, Throwable exception) {
        LocalDateTime finishedAt = LocalDateTime.now();
        long durationMs = Duration.between(startedAt, finishedAt).toMillis();

        int status = (exception != null) ? AiFlowExecution.STATUS_FAILED : AiFlowExecution.STATUS_COMPLETED;
        String errorCode = null;
        String errorMessage = null;
        if (exception != null) {
            errorCode = exception.getClass().getSimpleName();
            String msg = exception.getMessage();
            errorMessage = (msg != null && msg.length() > 2000) ? msg.substring(0, 2000) : msg;
        }

        LambdaUpdateWrapper<AiFlowExecution> updateWrapper = new LambdaUpdateWrapper<AiFlowExecution>()
                .eq(AiFlowExecution::getTraceId, traceId)
                .set(AiFlowExecution::getStatus, status)
                .set(AiFlowExecution::getFinishedAt, finishedAt)
                .set(AiFlowExecution::getDurationMs, durationMs)
                .set(AiFlowExecution::getErrorCode, errorCode)
                .set(AiFlowExecution::getErrorMessage, errorMessage)
                .set(AiFlowExecution::getUpdatedTime, finishedAt);

        aiFlowExecutionMapper.update(null, updateWrapper);
        log.debug("UPDATE ai_flow_execution, traceId={}, status={}, durationMs={}", traceId, status, durationMs);
    }
}