package org.lixiyun.server.hook.AgentHook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import org.lixiyun.server.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author lixiyun
 * @since 2026-01-26 14:52
 */
@Component
public abstract class BaseAgentHook extends AgentHook {

    protected Integer MAX_HISTORY_MESSAGES_ROUND_COUNT = 12;
    protected Integer MIN_HISTORY_MESSAGES_ROUND_COUNT = 6;

    @Autowired
    private ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
//        Map<String, Object> stateMap = conversationHistoryMessagesStorage.beforeAgent(state, config, MAX_HISTORY_MESSAGES_ROUND_COUNT);
//        super.beforeAgent(state, config);
//        Map<String, Object> map = this.beforeBaseAgent(state, config);
//        stateMap.putAll(map);
//        return CompletableFuture.completedFuture(stateMap);
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        // 在这个hook中可以通过this.name 获取对应的Agent名称
//        conversationHistoryMessagesStorage.afterAgent(state, config, true,
//                MAX_HISTORY_MESSAGES_ROUND_COUNT, MIN_HISTORY_MESSAGES_ROUND_COUNT);
//        super.afterAgent(state, config);
//        Map<String, Object> map = this.afterBaseAgent(state, config);
//        return CompletableFuture.completedFuture(map);
        return CompletableFuture.completedFuture(Map.of());
    }

    public Map<String, Object> beforeBaseAgent(OverAllState state, RunnableConfig config){
        return Map.of();
    }

    public Map<String, Object> afterBaseAgent(OverAllState state, RunnableConfig config){
        return Map.of();
    }
}
