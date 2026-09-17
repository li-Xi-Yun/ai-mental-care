package org.lixiyun.server.infrastructure.log.aspect;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.HistoryCompressionBO;
import org.lixiyun.pojo.entity.log.AiNodeExecution;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.infrastructure.log.FlowExecutionContextManager;
import org.lixiyun.server.mapper.AiNodeExecutionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI节点执行记录切面
 * <p>统一拦截 diagnosis 包和 conversation 包下所有节点的 apply 方法，
 * 在节点入口 INSERT 一条 ai_node_execution 记录，在 finally 中 UPDATE 该记录的状态、耗时等信息。</p>
 *
 * <h3>两个切点</h3>
 * <ul>
 *   <li>{@link #diagnosisNodePointcut()} — 拦截 diagnosis 包下实现 NodeExecutionSummary 的 apply(OverAllState, RunnableConfig)</li>
 *   <li>{@link #conversationNodePointcut()} — 拦截 conversation 包下的 apply(业务对象)，排除 OverAllState 参数</li>
 * </ul>
 *
 * <h3>降级策略</h3>
 * <p>若 FlowExecutionContextManager.existsContext(conversationId) 返回 false
 * （如流程入口 AOP 未执行或 Redis Key 已过期），切面跳过日志记录，仅执行原方法。</p>
 *
 * @author lixiyun
 * @since 2026-09-11
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class NodeExecutionAspect {

    /**
     * 流程执行上下文管理器，用于判断当前会话是否处于流程上下文中，以及获取 traceId、递增节点序号
     */
    private final FlowExecutionContextManager flowExecutionContextManager;
    /**
     * AI节点执行记录 Mapper，用于 INSERT / UPDATE ai_node_execution 表
     */
    private final AiNodeExecutionMapper aiNodeExecutionMapper;
    /**
     * AI节点配置缓存管理器，用于根据 nodeKey 获取 nodeConfigId
     */
    private final AiNodeConfigManager aiNodeConfigManager;

    // ==================== Diagnosis 节点切点 ====================

    /**
     * 切点：拦截 diagnosis 包下所有实现了 {@link NodeExecutionSummary} 接口的节点的
     * {@code apply(OverAllState, RunnableConfig)} 方法
     */
    @Pointcut(
            "execution(* org.lixiyun.server.ai.node.diagnosis..*.apply(com.alibaba.cloud.ai.graph.OverAllState, com.alibaba.cloud.ai.graph.RunnableConfig)) " +
            "&& target(org.lixiyun.server.ai.node.NodeExecutionSummary)"
    )
    public void diagnosisNodePointcut() {
    }

    /**
     * 环绕通知：拦截 diagnosis 节点的 apply 方法，记录节点执行日志
     * <ol>
     *   <li>从 OverAllState 中提取 conversationId，若上下文不存在则跳过日志直接执行原方法</li>
     *   <li>获取 traceId、递增 nodeSequence，记录 startedAt</li>
     *   <li>调用 {@link NodeExecutionSummary#inputSummary} 提取输入摘要，INSERT ai_node_execution 记录</li>
     *   <li>执行原方法 {@code pjp.proceed()}</li>
     *   <li>finally 中调用 {@link NodeExecutionSummary#outputSummary} 提取输出摘要，UPDATE 记录状态与耗时</li>
     * </ol>
     *
     * @param pjp 切点连接点，第一个参数为 OverAllState
     * @return 原方法的返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("diagnosisNodePointcut()")
    public Object aroundDiagnosisNode(ProceedingJoinPoint pjp) throws Throwable {
        OverAllState state = (OverAllState) pjp.getArgs()[0];

        Long conversationId = extractConversationIdFromState(state);
        if (conversationId == null || !flowExecutionContextManager.existsContext(conversationId)) {
            return pjp.proceed();
        }

        String nodeName = resolveNodeName(pjp);
        Long traceId = flowExecutionContextManager.getTraceId(conversationId);
        long nodeSequence = flowExecutionContextManager.incrementNodeSequence(conversationId);
        LocalDateTime startedAt = LocalDateTime.now();
        log.debug("[节点执行日志拦截器-图节点] conversationId={}, nodeName={}, traceId={}, nodeSequence={}", conversationId, nodeName, traceId, nodeSequence);

        NodeExecutionSummary summaryNode = (NodeExecutionSummary) pjp.getTarget();
        Map<String, Object> inputSummary = toSummaryMap(summaryNode.inputSummary(state));

        Long nodeConfigId = resolveNodeConfigId(nodeName);
        AiNodeExecution nodeExecution = AiNodeExecution.builder()
                .traceId(traceId)
                .nodeConfigId(nodeConfigId)
                .nodeKey(nodeName)
                .nodeName(nodeName)
                .nodeSequence((int) nodeSequence)
                .status(AiNodeExecution.STATUS_RUNNING)
                .startedAt(startedAt)
                .inputSummary(inputSummary)
                .build();
        aiNodeExecutionMapper.insert(nodeExecution);
        log.debug("[节点执行日志拦截器-图节点] INSERT traceId={}, nodeName={}, nodeSequence={}", traceId, nodeName, nodeSequence);

        Throwable caughtException = null;
        Object result = null;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            caughtException = t;
            throw t;
        } finally {
            try {
                Map<String, Object> outputSummary = toSummaryMap(summaryNode.outputSummary(state));
                updateNodeExecutionOnFinish(nodeExecution.getId(), startedAt, outputSummary, caughtException);
            } catch (Exception e) {
                log.error("[节点执行日志拦截器-图节点] UPDATE traceId={}, nodeName={}", traceId, nodeName, e);
            }
        }

        return result;
    }

    // ==================== Conversation 节点切点 ====================

    /**
     * 切点：拦截 conversation 包下所有节点的 {@code apply(..)} 方法，
     * 排除以 OverAllState 为首参的重载方法（避免与 diagnosis 切点重复拦截）
     */
    @Pointcut(
            "execution(* org.lixiyun.server.ai.node.conversation..*.apply(..)) " +
            "&& !execution(* org.lixiyun.server.ai.node.conversation..*.apply(com.alibaba.cloud.ai.graph.OverAllState, ..))"
    )
    public void conversationNodePointcut() {
    }

    /**
     * 环绕通知：拦截 conversation 节点的 apply 方法，记录节点执行日志
     * <ol>
     *   <li>从方法首参（ConversationProcessContextBO / HistoryCompressionBO）提取 conversationId</li>
     *   <li>若上下文不存在则跳过日志直接执行原方法</li>
     *   <li>获取 traceId、递增 nodeSequence，记录 startedAt</li>
     *   <li>提取输入摘要，INSERT ai_node_execution 记录</li>
     *   <li>执行原方法 {@code pjp.proceed()}</li>
     *   <li>finally 中提取输出摘要，UPDATE 记录状态与耗时</li>
     * </ol>
     *
     * @param pjp 切点连接点，第一个参数为业务上下文对象
     * @return 原方法的返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("conversationNodePointcut()")
    public Object aroundConversationNode(ProceedingJoinPoint pjp) throws Throwable {
        Long conversationId = extractConversationIdFromArg(pjp.getArgs()[0]);
        if (conversationId == null || !flowExecutionContextManager.existsContext(conversationId)) {
            return pjp.proceed();
        }

        String nodeName = resolveNodeName(pjp);
        Long traceId = flowExecutionContextManager.getTraceId(conversationId);
        long nodeSequence = flowExecutionContextManager.incrementNodeSequence(conversationId);
        LocalDateTime startedAt = LocalDateTime.now();
        log.debug("[节点执行日志拦截器-会话节点] conversationId={}, nodeName={}, traceId={}, nodeSequence={}", conversationId, nodeName, traceId, nodeSequence);

        Map<String, Object> inputSummary = extractConversationInputSummary(pjp.getArgs()[0]);

        Long nodeConfigId = resolveNodeConfigId(nodeName);
        AiNodeExecution nodeExecution = AiNodeExecution.builder()
                .traceId(traceId)
                .nodeConfigId(nodeConfigId)
                .nodeKey(nodeName)
                .nodeName(nodeName)
                .nodeSequence((int) nodeSequence)
                .status(AiNodeExecution.STATUS_RUNNING)
                .startedAt(startedAt)
                .inputSummary(inputSummary)
                .build();
        aiNodeExecutionMapper.insert(nodeExecution);
        log.debug("[节点执行日志拦截器-会话节点] INSERT traceId={}, nodeName={}, nodeSequence={}", traceId, nodeName, nodeSequence);

        Throwable caughtException = null;
        Object result = null;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            caughtException = t;
            throw t;
        } finally {
            try {
                Map<String, Object> outputSummary = extractConversationOutputSummary(result);
                updateNodeExecutionOnFinish(nodeExecution.getId(), startedAt, outputSummary, caughtException);
            } catch (Exception e) {
                log.error("[节点执行日志拦截器-会话节点] UPDATE traceId={}, nodeName={}", traceId, nodeName, e);
            }
        }

        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 从 OverAllState 中提取 conversationId
     *
     * @param state 诊断节点的全局状态对象
     * @return conversationId，若元数据不存在则返回 null
     */
    private Long extractConversationIdFromState(OverAllState state) {
        ConversationMetadata metadata = (ConversationMetadata) state.value(ConversationMetadata.NAME).orElse(null);
        return metadata != null ? metadata.getConversationId() : null;
    }

    /**
     * 从 conversation 节点的方法首参中提取 conversationId
     * <p>支持 {@link ConversationProcessContextBO} 和 {@link HistoryCompressionBO} 两种参数类型。</p>
     *
     * @param arg apply 方法的第一个参数
     * @return conversationId，若无法识别参数类型或会话为 null 则返回 null
     */
    private Long extractConversationIdFromArg(Object arg) {
        if (arg instanceof ConversationProcessContextBO ctx) {
            return ctx.getConversation() != null ? ctx.getConversation().getId() : null;
        } else if (arg instanceof HistoryCompressionBO bo) {
            return bo.getConversation() != null ? bo.getConversation().getId() : null;
        }
        return null;
    }

    /**
     * 解析节点名称：优先读取目标类的 {@code NODE_NAME} 静态字段，若读取失败则回退到类简单名
     *
     * @param pjp 切点连接点
     * @return 节点名称
     */
    private String resolveNodeName(ProceedingJoinPoint pjp) {
        try {
            Class<?> nodeClass = pjp.getSignature().getDeclaringType();
            return (String) nodeClass.getDeclaredField("NODE_NAME").get(null);
        } catch (Exception e) {
            return pjp.getSignature().getDeclaringType().getSimpleName();
        }
    }

    /**
     * 根据节点名称（nodeKey）解析节点模型配置ID
     *
     * @param nodeKey 节点唯一标识
     * @return 节点配置ID，若配置不存在则返回 null
     */
    private Long resolveNodeConfigId(String nodeKey) {
        AiNodeConfig config = aiNodeConfigManager.getConfig(nodeKey);
        if (config == null) {
            log.warn("节点配置未找到，nodeConfigId 将为 NULL，nodeKey：{}", nodeKey);
            return null;
        }
        return config.getId();
    }

    /**
     * 将摘要对象转换为 {@code Map<String, Object>}
     * <p>若已是 Map 则直接返回；否则序列化为 JSON 再反序列化为 Map，包裹在 "data" 键下。</p>
     *
     * @param summary 摘要对象，可为 null
     * @return 转换后的 Map，若 summary 为 null 则返回 null
     */
    private Map<String, Object> toSummaryMap(Object summary) {
        if (summary == null) {
            return null;
        }
        if (summary instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) summary;
            return map;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("data", JsonUtils.parseMap(JsonUtils.toJsonString(summary)));
        return map;
    }

    /**
     * 从 conversation 节点的方法首参中提取输入摘要
     * <p>根据参数类型提取关键字段：conversationId、currentRound、消息数量等。</p>
     *
     * @param arg apply 方法的第一个参数
     * @return 输入摘要 Map
     */
    private Map<String, Object> extractConversationInputSummary(Object arg) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (arg instanceof ConversationProcessContextBO ctx) {
            if (ctx.getConversation() != null) {
                summary.put("conversationId", ctx.getConversation().getId());
                summary.put("currentRound", ctx.getConversation().getCurrentRound());
            }
            summary.put("temporaryMessageCount", ctx.getTemporaryMessages() != null ? ctx.getTemporaryMessages().size() : 0);
        } else if (arg instanceof HistoryCompressionBO bo) {
            if (bo.getConversation() != null) {
                summary.put("conversationId", bo.getConversation().getId());
            }
            summary.put("historyMessageCount", bo.getHistoryMessages() != null ? bo.getHistoryMessages().size() : 0);
            summary.put("emotionAnalysisCount", bo.getEmotionAnalyses() != null ? bo.getEmotionAnalyses().size() : 0);
        }
        return summary;
    }

    /**
     * 从 conversation 节点的返回值中提取输出摘要
     * <p>若返回值为 String，截取前 500 字符；否则通过 {@link #toSummaryMap} 转换。</p>
     *
     * @param result 原方法的返回值，可为 null
     * @return 输出摘要 Map，若 result 为 null 则返回 null
     */
    private Map<String, Object> extractConversationOutputSummary(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof String s) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("result", s.length() > 500 ? s.substring(0, 500) : s);
            return map;
        }
        return toSummaryMap(result);
    }

    /**
     * 节点执行结束时更新 ai_node_execution 记录
     * <p>计算耗时，根据是否有异常设置状态为 COMPLETED 或 FAILED，
     * 异常信息截取前 2000 字符存入 error_message。</p>
     *
     * @param id             记录主键，作为更新条件
     * @param startedAt      节点开始时间，用于计算 durationMs
     * @param outputSummary  输出摘要
     * @param exception      原方法抛出的异常，为 null 表示成功
     */
    private void updateNodeExecutionOnFinish(Long id, LocalDateTime startedAt, Map<String, Object> outputSummary, Throwable exception) {
        LocalDateTime finishedAt = LocalDateTime.now();
        long durationMs = Duration.between(startedAt, finishedAt).toMillis();

        int status = (exception != null) ? AiNodeExecution.STATUS_FAILED : AiNodeExecution.STATUS_COMPLETED;
        String errorCode = null;
        String errorMessage = null;
        if (exception != null) {
            errorCode = exception.getClass().getSimpleName();
            String msg = exception.getMessage();
            errorMessage = (msg != null && msg.length() > 2000) ? msg.substring(0, 2000) : msg;
        }

        LambdaUpdateWrapper<AiNodeExecution> updateWrapper = new LambdaUpdateWrapper<AiNodeExecution>()
                .eq(AiNodeExecution::getId, id)
                .set(AiNodeExecution::getStatus, status)
                .set(AiNodeExecution::getFinishedAt, finishedAt)
                .set(AiNodeExecution::getDurationMs, durationMs)
                .set(AiNodeExecution::getOutputSummary, outputSummary != null ? JsonUtils.toJsonString(outputSummary) : null)
                .set(AiNodeExecution::getErrorCode, errorCode)
                .set(AiNodeExecution::getErrorMessage, errorMessage);

        aiNodeExecutionMapper.update(null, updateWrapper);
        log.debug("UPDATE ai_node_execution, id={}, status={}, durationMs={}", id, status, durationMs);
    }
}