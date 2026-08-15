package org.lixiyun.server.ai.model.processor.decorator;

import com.alibaba.cloud.ai.graph.NodeOutput;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;
import org.lixiyun.server.ai.model.processor.api.StreamEventListener;
import org.lixiyun.server.ai.model.processor.core.AgentStreamDecorator;
import reactor.core.publisher.Flux;

/**
 * 业务回调分发装饰器
 * <p>
 * 将领域事件转发给上层业务 {@link StreamEventListener}，
 * 替代原有的零散 {@code Consumer} 回调参数设计。
 * </p>
 *
 * <h3>事件→回调映射：</h3>
 * <ul>
 *     <li>ModelContentChunk → {@link StreamEventListener#onContentChunk}</li>
 *     <li>ModelThinkChunk → {@link StreamEventListener#onThinkChunk}</li>
 *     <li>FullThinkCompleted → {@link StreamEventListener#onFullThinkCompleted}</li>
 *     <li>ModelComplete → {@link StreamEventListener#onModelComplete}</li>
 *     <li>ModelToolCall → {@link StreamEventListener#onToolCall}</li>
 *     <li>ToolResponseReceived → {@link StreamEventListener#onToolResponse}</li>
 *     <li>StreamError → {@link StreamEventListener#onError}</li>
 *     <li>StreamFinished → {@link StreamEventListener#onFinished}</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Slf4j
public class ListenerDispatchDecorator extends AgentStreamDecorator {

    private final StreamEventListener listener;

    public ListenerDispatchDecorator(AgentStreamProcessor delegate, StreamEventListener listener) {
        super(delegate);
        this.listener = listener;
    }

    @Override
    public Flux<AgentStreamEvent> process(Flux<NodeOutput> rawOutputFlux) {
        return delegate.process(rawOutputFlux)
                .doOnNext(this::dispatch)
                // 兜底：无论成功、失败、取消，最终都会执行一次收尾
                .doFinally(signalType -> listener.onFinished());
    }

    private void dispatch(AgentStreamEvent event) {
        if (event instanceof AgentStreamEvent.ModelContentChunk chunk) {
            listener.onContentChunk(chunk.text());
        } else if (event instanceof AgentStreamEvent.ModelThinkChunk chunk) {
            listener.onThinkChunk(chunk.reasoning());
        } else if (event instanceof AgentStreamEvent.FullThinkCompleted completed) {
            listener.onFullThinkCompleted(completed.fullText());
        } else if (event instanceof AgentStreamEvent.ModelComplete complete) {
            listener.onModelComplete(complete.message());
        } else if (event instanceof AgentStreamEvent.ModelToolCall toolCall) {
            listener.onToolCall(toolCall.message());
        } else if (event instanceof AgentStreamEvent.ToolResponseReceived resp) {
            listener.onToolResponse(resp.message());
        } else if (event instanceof AgentStreamEvent.StreamError err) {
            listener.onError(err.throwable());
        }
    }
}