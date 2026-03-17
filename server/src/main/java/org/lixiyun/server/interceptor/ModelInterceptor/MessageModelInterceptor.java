package org.lixiyun.server.interceptor.ModelInterceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-01-21 10:13
 */
@Slf4j
@Component(MessageModelInterceptor.NAME)
public class MessageModelInterceptor extends BaseModelInterceptor {

    public static final String NAME = "MessageModelInterceptor";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ModelResponse interceptBaseModel(ModelRequest request, ModelCallHandler handler) {
        // 执行实际调用
        ModelResponse response = handler.call(request);

        return response;
    }

}