package org.lixiyun.server.interceptor.ToolInterceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-01-26 16:02
 */
@Slf4j
@Component
public abstract class BaseToolInterceptor extends ToolInterceptor {

    @Autowired
    private ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        // 这里的两个参数信息中都不能获取到OverAllState参数，只能读取到RunnableConfig参数
        String toolName = request.getToolName();
        String agentName = (String) request.getContext().get("_AGENT_");
        long startTime = System.currentTimeMillis();

        log.debug("执行工具: {}", toolName);

        try {
            ToolCallResponse response = this.interceptBaseToolCall(request, handler);

            long duration = System.currentTimeMillis() - startTime;

            log.debug("Agent {} 执行工具 {} 成功 (耗时: {}ms)", agentName, toolName, duration);

            if(duration > 5000){
                log.warn("Agent {} 执行工具 {} 耗时过长 (耗时: {}ms)", agentName, toolName, duration);
            }

            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Agent {} 执行工具 {} 失败 (耗时: {}ms): {}", agentName, toolName, duration, e.getMessage());

            return ToolCallResponse.of(
                    request.getToolCallId(),
                    request.getToolName(),
                    "工具执行失败: " + e.getMessage()
            );
        }
    }

    public abstract ToolCallResponse interceptBaseToolCall(ToolCallRequest request, ToolCallHandler handler);
}
