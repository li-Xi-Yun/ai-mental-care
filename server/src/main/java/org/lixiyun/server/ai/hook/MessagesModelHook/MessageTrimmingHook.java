package org.lixiyun.server.ai.hook.MessagesModelHook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-01-21 9:42
 */
@Slf4j
@HookPositions({HookPosition.AFTER_MODEL, HookPosition.BEFORE_MODEL})
public class MessageTrimmingHook extends MessagesModelHook {

    @Override
    public String getName() {
        return "message_trimming";
    }

    @Override
    public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
        return super.afterModel(previousMessages, config);
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
         // 如果消息数量超过限制，只保留最后 MAX_MESSAGES 条消息
        log.debug("消息数量: {}", previousMessages.size());
         // 如果消息数量未超过限制，返回原始消息（不进行修改）
         return new AgentCommand(previousMessages);
    }
}