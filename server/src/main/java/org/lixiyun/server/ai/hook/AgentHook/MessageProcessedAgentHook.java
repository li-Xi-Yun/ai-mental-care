package org.lixiyun.server.ai.hook.AgentHook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author lixiyun
 * @since 2026-01-21 11:27
 */
@Slf4j
@Component(MessageProcessedAgentHook.NAME)
@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class MessageProcessedAgentHook extends BaseAgentHook {

    public static final String NAME = "MessageProcessedAgentHook";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, Object> beforeBaseAgent(OverAllState state, RunnableConfig config) {
        return super.beforeBaseAgent(state, config);
    }

    @Override
    public Map<String, Object> afterBaseAgent(OverAllState state, RunnableConfig config) {
        return super.afterBaseAgent(state, config);
    }

}
