package org.lixiyun.server.ai.interceptor.ToolInterceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-01-22 11:36
 */
@Slf4j
@Component(MessageToolInterceptor.NAME)
public class MessageToolInterceptor extends BaseToolInterceptor {

    public static final String NAME = "MessageToolInterceptor";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolCallResponse interceptBaseToolCall(ToolCallRequest request, ToolCallHandler handler) {
            ToolCallResponse response = handler.call(request);
            return response;
    }

}
