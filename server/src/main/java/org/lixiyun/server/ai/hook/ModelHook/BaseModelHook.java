package org.lixiyun.server.ai.hook.ModelHook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author lixiyun
 * @since 2026-01-27 16:03
 */
@Slf4j
@Component
public abstract class BaseModelHook extends ModelHook {

    @Autowired
    private ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        // 在模型调用前执行
//        conversationHistoryMessagesStorage.beforeModel(state, config);
//        Map<String, Object> map = this.beforeBaseModel(state, config);
//        return CompletableFuture.completedFuture(map);
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
//        conversationHistoryMessagesStorage.afterModel(state, config);
//        Map<String, Object> map = this.afterBaseModel(state, config);
//         可以记录响应信息
//        return CompletableFuture.completedFuture(map);
        return CompletableFuture.completedFuture(Map.of());
    }

    public Map<String, Object> beforeBaseModel(OverAllState state, RunnableConfig config){
        return Map.of();
    }

    public Map<String, Object> afterBaseModel(OverAllState state, RunnableConfig config){
        return Map.of();
    }
}
