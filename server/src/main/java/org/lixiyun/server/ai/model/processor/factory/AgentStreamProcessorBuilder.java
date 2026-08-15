package org.lixiyun.server.ai.model.processor.factory;

import org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;
import org.lixiyun.server.ai.model.processor.api.StreamEventListener;
import org.lixiyun.server.ai.model.processor.core.RootAgentStreamProcessor;
import org.lixiyun.server.ai.model.processor.decorator.ListenerDispatchDecorator;
import org.lixiyun.server.ai.model.processor.decorator.LoggingDecorator;
import org.lixiyun.server.ai.model.processor.decorator.MessagePersistDecorator;
import org.lixiyun.server.ai.model.processor.decorator.ThinkAccumulateDecorator;

/**
 * 流式处理器链路组装建造器
 * <p>
 * 封装装饰器的层层嵌套构造，业务方通过链式调用按需装配能力，
 * 无需手动 {@code new} 多层装饰器。
 * </p>
 *
 * <h3>组装顺序（从内到外）：</h3>
 * <pre>
 * RootAgentStreamProcessor
 *   → ThinkAccumulateDecorator
 *     → LoggingDecorator
 *       → MessagePersistDecorator
 *         → ListenerDispatchDecorator
 * </pre>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * AgentStreamProcessor processor = AgentStreamProcessorBuilder.create()
 *         .withThinkAccumulate()
 *         .withLogging(conversationId)
 *         .withPersistence(storage, userId, conversationId, round)
 *         .withListener(new StreamEventListenerAdapter() {
 *             @Override
 *             public void onContentChunk(String text) {
 *                 webSocketService.send(userId, text);
 *             }
 *         })
 *         .build();
 *
 * processor.process(rawFlux)
 *         .subscribeOn(Schedulers.boundedElastic())
 *         .subscribe();
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
public class AgentStreamProcessorBuilder {

    private AgentStreamProcessor processor;

    private AgentStreamProcessorBuilder(AgentStreamProcessor root) {
        this.processor = root;
    }

    /**
     * 创建建造器，使用默认思考内容 Key（reasoningContent）
     *
     * @return 建造器实例
     */
    public static AgentStreamProcessorBuilder create() {
        return new AgentStreamProcessorBuilder(new RootAgentStreamProcessor());
    }

    /**
     * 创建建造器，指定思考内容元数据 Key
     *
     * @param reasoningMetaKey 思考内容元数据 Key（如 DashScope 使用 "thinking"）
     * @return 建造器实例
     */
    public static AgentStreamProcessorBuilder create(String reasoningMetaKey) {
        return new AgentStreamProcessorBuilder(new RootAgentStreamProcessor(reasoningMetaKey));
    }

    /**
     * 创建建造器，指定根处理器
     *
     * @param root 根处理器 {@link AgentStreamProcessor} 、{@link RootAgentStreamProcessor}
     * @return 建造器实例
     */
    public static AgentStreamProcessorBuilder create(AgentStreamProcessor root) {
        return new AgentStreamProcessorBuilder(root);
    }

    /**
     * 叠加思考内容聚合能力
     * <p>
     * 收集所有 ModelThinkChunk，在流结束时发射 FullThinkCompleted 事件
     * </p>
     *
     * @return 当前建造器
     */
    public AgentStreamProcessorBuilder withThinkAccumulate() {
        this.processor = new ThinkAccumulateDecorator(processor);
        return this;
    }

    /**
     * 叠加消息持久化能力
     * <p>
     * 将 ModelComplete / ModelToolCall / ToolResponseReceived / FullThinkCompleted 事件持久化到数据库
     * </p>
     *
     * @param storage        会话历史消息存储服务
     * @param userId         用户ID
     * @param conversationId 会话ID
     * @param round          当前对话轮次
     * @return 当前建造器
     */
    public AgentStreamProcessorBuilder withPersistence(ConversationHistoryMessagesStorage storage,
                                                       Long userId,
                                                       Long conversationId,
                                                       int round) {
        this.processor = new MessagePersistDecorator(processor, storage, userId, conversationId, round);
        return this;
    }

    /**
     * 叠加业务回调分发能力
     * <p>
     * 将领域事件转发给 StreamEventListener，替代零散 Consumer 回调
     * </p>
     *
     * @param listener 业务事件监听器
     * @return 当前建造器
     */
    public AgentStreamProcessorBuilder withListener(StreamEventListener listener) {
        this.processor = new ListenerDispatchDecorator(processor, listener);
        return this;
    }

    /**
     * 叠加日志埋点能力
     *
     * @param conversationId 会话ID（用于日志关联）
     * @return 当前建造器
     */
    public AgentStreamProcessorBuilder withLogging(Long conversationId) {
        this.processor = new LoggingDecorator(processor, conversationId);
        return this;
    }

    /**
     * 构建最终处理器链路
     *
     * @return 组装完成的 AgentStreamProcessor
     */
    public AgentStreamProcessor build() {
        return processor;
    }
}