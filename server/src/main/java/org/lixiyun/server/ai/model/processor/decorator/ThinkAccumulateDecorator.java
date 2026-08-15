package org.lixiyun.server.ai.model.processor.decorator;

import com.alibaba.cloud.ai.graph.NodeOutput;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;
import org.lixiyun.server.ai.model.processor.core.AgentStreamDecorator;
import reactor.core.publisher.Flux;

/**
 * 思考内容聚合装饰器
 * <p>
 * 持续收集 {@link AgentStreamEvent.ModelThinkChunk} 事件，
 * 在流结束时发射 {@link AgentStreamEvent.FullThinkCompleted} 事件，
 * 携带完整的思考推理文本，供下游装饰器（如持久化）使用。
 * </p>
 *
 * <h3>处理逻辑：</h3>
 * <ol>
 *     <li>透传所有原始事件（不修改、不吞没）</li>
 *     <li>副作用累积：每收到 ModelThinkChunk，追加到内部 StringBuilder</li>
 *     <li>流结束信号：在 StreamFinished 之前插入 FullThinkCompleted（如有思考内容）</li>
 * </ol>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Slf4j
public class ThinkAccumulateDecorator extends AgentStreamDecorator {

    public ThinkAccumulateDecorator(AgentStreamProcessor delegate) {
        super(delegate);
    }

    @Override
    public Flux<AgentStreamEvent> process(Flux<NodeOutput> rawOutputFlux) {
        return Flux.defer(() -> {
            StringBuilder thinkBuilder = new StringBuilder();
            return delegate.process(rawOutputFlux)
                    .doOnNext(event -> {
                        if (event instanceof AgentStreamEvent.ModelThinkChunk chunk) {
                            thinkBuilder.append(chunk.reasoning());
                        }
                    })
                    .flatMap(event -> {
                        if (event instanceof AgentStreamEvent.StreamFinished) {
                            if (!thinkBuilder.isEmpty()) {
                                log.debug("思考内容聚合完成，总长度：{}", thinkBuilder.length());
                                return Flux.just(
                                        new AgentStreamEvent.FullThinkCompleted(thinkBuilder.toString()),
                                        event
                                );
                            }
                        }
                        return Flux.just(event);
                    });
        });
    }

}