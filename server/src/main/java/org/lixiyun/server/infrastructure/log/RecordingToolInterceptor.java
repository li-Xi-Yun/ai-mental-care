package org.lixiyun.server.infrastructure.log;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.interceptor.ToolInterceptor.BaseToolInterceptor;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模型工具调用日志拦截器
 *
 * <p>继承{@link BaseToolInterceptor}，在{@code interceptBaseToolCall}中完成工具调用的完整日志记录，
 * 记录每次工具调用的名称、调用ID、所属Agent、所在节点、会话上下文、耗时等信息。</p>
 *
 * <h3>上下文传播机制</h3>
 * <p>业务上下文（conversationId、userId、currentRound）和节点配置（AiNodeConfig）的传播链路：</p>
 * <pre>
 * Graph构建RunnableConfig（注入ConversationMetadata + AiNodeConfig）
 *   → Node.apply(state, config)
 *     → Model.callForResult(chatModel, prompt, aiNodeConfig, config)
 *       → ReactAgent调用工具
 *         → 框架将RunnableConfig.metadata()注入ToolCallRequest.context
 *           → 本拦截器通过request.getContext()获取ConversationMetadata和AiNodeConfig
 * </pre>
 *
 * <h3>记录字段</h3>
 * <table>
 *   <tr><th>字段</th><th>来源</th></tr>
 *   <tr><td>toolName</td><td>{@code request.getToolName()}</td></tr>
 *   <tr><td>toolCallId</td><td>{@code request.getToolCallId()}</td></tr>
 *   <tr><td>agentName</td><td>{@code request.getContext().get("_AGENT_")}</td></tr>
 *   <tr><td>nodeKey / nodeName / modelProvider</td><td>{@code request.getContext().get(AiNodeConfig.NAME)}</td></tr>
 *   <tr><td>conversationId / userId / roundNum</td><td>{@code request.getContext().get(ConversationMetadata.NAME)}</td></tr>
 *   <tr><td>traceId</td><td>{@link FlowExecutionContextManager} 从Redis获取</td></tr>
 *   <tr><td>durationMs</td><td>System.currentTimeMillis()差值</td></tr>
 *   <tr><td>调用状态</td><td>成功/失败（含异常信息）</td></tr>
 * </table>
 *
 * <h3>开关控制</h3>
 * <p>通过{@code ai.evaluation.recording.enabled=true}配置开启，默认关闭。</p>
 *
 * <h3>异常安全</h3>
 * <p>日志记录在finally块中执行，即使记录失败也不影响工具调用的正常执行。
 * 工具调用异常会被捕获并重新抛出，确保上层逻辑能感知到异常。</p>
 *
 * @author lixiyun
 * @since 2026-09-17
 * @see BaseToolInterceptor
 * @see RecordingModelInterceptor
 * @see ConversationMetadata
 * @see AiNodeConfig
 */
@Slf4j
@Component(RecordingToolInterceptor.NAME)
@ConditionalOnProperty(name = "ai.evaluation.recording.enabled", havingValue = "true")
public class RecordingToolInterceptor extends BaseToolInterceptor {

    public static final String NAME = "RecordingToolInterceptor";

    private final FlowExecutionContextManager flowExecutionContextManager;

    public RecordingToolInterceptor(FlowExecutionContextManager flowExecutionContextManager) {
        this.flowExecutionContextManager = flowExecutionContextManager;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 拦截工具调用，记录完整的调用信息并输出日志
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>从请求的{@code RunnableConfig}上下文中提取节点配置（{@link AiNodeConfig}）、
     *       会话元数据（{@link ConversationMetadata}）、traceId等请求侧信息</li>
     *   <li>调用{@code handler.call(request)}执行实际工具调用</li>
     *   <li>在finally中记录调用耗时、成功/失败状态、异常信息等</li>
     * </ol>
     *
     * <p>异常安全：工具调用异常会被捕获记录后重新抛出，日志记录失败不影响业务调用。</p>
     *
     * @param request 工具调用请求，包含toolName、toolCallId、context等
     * @param handler 调用链下游处理器
     * @return 工具调用响应
     */
    @Override
    public ToolCallResponse interceptBaseToolCall(ToolCallRequest request, ToolCallHandler handler) {
        String toolName = request.getToolName();
        String toolCallId = request.getToolCallId();
        String agentName = (String) request.getContext().get("_AGENT_");

        ConversationMetadata conversationMetadata =
                (ConversationMetadata) request.getContext().get(ConversationMetadata.NAME);
        log.debug("[工具调用日志拦截器] 会话上下文: {}", conversationMetadata);

        AiNodeConfig aiNodeConfig = (AiNodeConfig) request.getContext().get(AiNodeConfig.NAME);
        log.debug("[工具调用日志拦截器] 节点配置: {}", aiNodeConfig);

        String nodeKey = aiNodeConfig != null ? aiNodeConfig.getNodeKey() : "unknown";
        String nodeName = aiNodeConfig != null ? aiNodeConfig.getNodeName() : "unknown";
        String modelProvider = aiNodeConfig != null
                ? ChatModelType.fromType(aiNodeConfig.getModelType()).factoryKey : "unknown";

        Long traceId = null;
        if (conversationMetadata != null) {
            traceId = flowExecutionContextManager.getTraceId(conversationMetadata.getConversationId());
            log.debug("[工具调用日志拦截器] traceId={}", traceId);
        }

        log.info("[工具调用日志拦截器] 开始调用工具: toolName={}, toolCallId={}, agent={}, nodeKey={}, nodeName={}, modelProvider={}",
                toolName, toolCallId, agentName, nodeKey, nodeName, modelProvider);

        long start = System.currentTimeMillis();
        ToolCallResponse response = null;
        Throwable caughtException = null;
        try {
            response = handler.call(request);
            log.debug("[工具调用日志拦截器] 工具调用响应: toolName={}, toolCallId={}, response={}",
                    toolName, toolCallId, response);
        } catch (Throwable t) {
            caughtException = t;
            log.error("[工具调用日志拦截器] 工具调用异常: toolName={}, toolCallId={}, agent={}, nodeKey={}, nodeName={}, error={}",
                    toolName, toolCallId, agentName, nodeKey, nodeName, t.getMessage(), t);
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - start;

            if (caughtException == null && response != null) {
                log.info("[工具调用日志拦截器] 工具调用完成: toolName={}, toolCallId={}, agent={}, nodeKey={}, nodeName={}, duration={}ms",
                        toolName, toolCallId, agentName, nodeKey, nodeName, durationMs);
                if (durationMs > 5000) {
                    log.warn("[工具调用日志拦截器] 工具调用耗时过长: toolName={}, toolCallId={}, agent={}, nodeKey={}, nodeName={}, duration={}ms",
                            toolName, toolCallId, agentName, nodeKey, nodeName, durationMs);
                }
            } else {
                String errorCode = caughtException != null
                        ? caughtException.getClass().getSimpleName() : "unknown";
                String errorMessage = caughtException != null ? caughtException.getMessage() : "unknown";
                log.error("[工具调用日志拦截器] 工具调用失败: toolName={}, toolCallId={}, agent={}, nodeKey={}, nodeName={}, duration={}ms, errorCode={}, errorMessage={}",
                        toolName, toolCallId, agentName, nodeKey, nodeName, durationMs, errorCode, errorMessage);
            }
        }

        return response;
    }
}