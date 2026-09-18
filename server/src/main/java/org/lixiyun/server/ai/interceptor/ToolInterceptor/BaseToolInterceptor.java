package org.lixiyun.server.ai.interceptor.ToolInterceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage;
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
        ToolCallResponse response = this.interceptBaseToolCall(request, handler);
        return response;

    }

    public abstract ToolCallResponse interceptBaseToolCall(ToolCallRequest request, ToolCallHandler handler);
}
