package org.lixiyun.server.ai.model.processor.core;

import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;

/**
 * 装饰器抽象基类
 * <p>
 * 所有增强装饰器继承此类，持有下游处理器 {@code delegate}，形成链式调用。
 * 子类只需重写 {@link #process} 方法，在委托调用前后叠加横切能力。
 * </p>
 *
 * <h3>装饰器链示例：</h3>
 * <pre>
 * RootProcessor → ThinkAccumulateDecorator → MessagePersistDecorator → ListenerDispatchDecorator
 * </pre>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
public abstract class AgentStreamDecorator implements AgentStreamProcessor {

    protected final AgentStreamProcessor delegate;

    protected AgentStreamDecorator(AgentStreamProcessor delegate) {
        this.delegate = delegate;
    }
}