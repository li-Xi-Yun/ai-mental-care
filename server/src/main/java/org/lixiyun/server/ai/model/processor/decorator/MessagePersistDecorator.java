package org.lixiyun.server.ai.model.processor.decorator;

import com.alibaba.cloud.ai.graph.NodeOutput;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.lixiyun.server.ai.message.ThinkMessage;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;
import org.lixiyun.server.ai.model.processor.core.AgentStreamDecorator;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

/**
 * 消息持久化装饰器
 * <p>
 * 将关键对话数据自动持久化到数据库，支持以下事件类型的存储：
 * </p>
 * <ul>
 *     <li>{@link AgentStreamEvent.ModelComplete} → 存储为 ASSISTANT 类型消息</li>
 *     <li>{@link AgentStreamEvent.ModelToolCall} → 存储为 ASSISTANT_TOOL 类型消息</li>
 *     <li>{@link AgentStreamEvent.ToolResponseReceived} → 存储为 TOOL 类型消息</li>
 *     <li>{@link AgentStreamEvent.FullThinkCompleted} → 存储为 THINKING 类型消息</li>
 * </ul>
 *
 * <h3>可插拔特性：</h3>
 * <p>
 * 不需要持久化的场景（如内部工具链式调用、临时推理）直接不装配此装饰器即可。
 * </p>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Slf4j
public class MessagePersistDecorator extends AgentStreamDecorator {

    private final ConversationHistoryMessagesStorage storage;
    private final Long userId;
    private final Long conversationId;
    private final int round;

    public MessagePersistDecorator(AgentStreamProcessor delegate,
                                   ConversationHistoryMessagesStorage storage,
                                   Long userId,
                                   Long conversationId,
                                   int round) {
        super(delegate);
        this.storage = storage;
        this.userId = userId;
        this.conversationId = conversationId;
        this.round = round;
    }

    @Override
    public Flux<AgentStreamEvent> process(Flux<NodeOutput> rawOutputFlux) {
        return delegate.process(rawOutputFlux)
                .doOnNext(this::persistIfNeeded);
    }

    private void persistIfNeeded(AgentStreamEvent event) {
        if (event instanceof AgentStreamEvent.ModelComplete complete) {
            storage.save(userId, conversationId, round, complete.message());
            log.info("持久化模型完成消息，会话ID：{}，轮次：{}", conversationId, round);
        } else if (event instanceof AgentStreamEvent.ModelToolCall toolCall) {
            storage.save(userId, conversationId, round, toolCall.message());
            log.debug("持久化工具调用请求，会话ID：{}，轮次：{}", conversationId, round);
        } else if (event instanceof AgentStreamEvent.ToolResponseReceived toolResp) {
            storage.save(userId, conversationId, round, toolResp.message());
            log.debug("持久化工具执行结果，会话ID：{}，轮次：{}", conversationId, round);
        } else if (event instanceof AgentStreamEvent.FullThinkCompleted thinkCompleted) {
            Message thinkMessage = new ThinkMessage(thinkCompleted.fullText());
            storage.save(userId, conversationId, round, thinkMessage);
            log.debug("持久化思考内容，会话ID：{}，轮次：{}，长度：{}",
                    conversationId, round, thinkCompleted.fullText().length());
        }
    }
}