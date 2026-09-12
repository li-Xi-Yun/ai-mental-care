package org.lixiyun.server.ai.interceptor.ModelInterceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.springframework.stereotype.Component;

/**
 * @author lixiyun
 * @since 2026-01-26 15:56
 */
@Slf4j
@Component
public abstract class BaseModelInterceptor extends ModelInterceptor {

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // 请求前记录
        log.debug("发送请求到模型: {} 条消息", request.getMessages().size());

        long startTime = System.currentTimeMillis();

        // 执行实际调用
        ModelResponse response = this.interceptBaseModel(request, handler);

        // 响应后记录
        long duration = System.currentTimeMillis() - startTime;
        log.debug("模型响应耗时: {}ms, {}s", duration, duration / 1000);
        if(duration > 5000){
            ConversationMetadata metadata = (ConversationMetadata) request.getContext().get(ConversationMetadata.NAME);
            String modelName = request.getOptions() != null ? request.getOptions().getModel() : "unknown";
            if (metadata != null) {
                log.warn("会话[{}] 用户[{}] 轮次[{}] 模型[{}] 响应时间过长({}ms)，请检查代码",
                        metadata.getConversationId(), metadata.getUserId(),
                        metadata.getCurrentRound(), modelName, duration);
            } else {
                log.warn("模型[{}] 响应时间过长({}ms)，请检查代码", modelName, duration);
            }
        }

        return response;
    }

    public abstract ModelResponse interceptBaseModel(ModelRequest request, ModelCallHandler handler);
}