package org.lixiyun.server.hook.ModelHook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author lixiyun
 * @since 2026-01-21 16:48
 */
@Slf4j
@Component(MessageProcessedModelHook.NAME)
@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
public class MessageProcessedModelHook extends BaseModelHook {

    public static final String NAME = "MessageProcessedModelHook";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, Object> beforeBaseModel(OverAllState state, RunnableConfig config) {
        return Map.of();
    }

    @Override
    public Map<String, Object> afterBaseModel(OverAllState state, RunnableConfig config) {
        return Map.of();
    }
}
