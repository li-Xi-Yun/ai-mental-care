package org.lixiyun.server.ai.model.processor.api;

import com.alibaba.cloud.ai.graph.NodeOutput;
import reactor.core.publisher.Flux;

/**
 * 流式处理器顶层接口
 * <p>
 * 所有处理器（裸处理器、装饰器）实现同一接口，
 * 通过 {@link Flux} 响应式管道实现事件流的链式传递与能力叠加。
 * </p>
 *
 * <h3>设计契约：</h3>
 * <ul>
 *     <li>输入：原始 {@code Flux<NodeOutput>}（来自 AI Graph SDK）</li>
 *     <li>输出：领域事件流 {@code Flux<AgentStreamEvent>}（已解耦 SDK 依赖）</li>
 *     <li>调用方通过 {@code .subscribe()} 或 {@code .blockLast()} 消费最终事件流</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
public interface AgentStreamProcessor {

    /**
     * 处理原始模型输出流，转换为领域事件流
     *
     * @param rawOutputFlux AI Graph SDK 的原始节点输出流
     * @return 领域事件流，包含解析后的所有 {@link AgentStreamEvent}
     */
    Flux<AgentStreamEvent> process(Flux<NodeOutput> rawOutputFlux);
}