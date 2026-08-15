package org.lixiyun.server.ai.model.processor.decorator;

import com.alibaba.cloud.ai.graph.NodeOutput;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;
import org.lixiyun.server.ai.model.processor.core.AgentStreamDecorator;
import reactor.core.publisher.Flux;

/**
 * 日志埋点装饰器
 * <p>
 * 对关键事件进行日志记录，便于运行时追踪与问题排查。
 * 可按需装配，不影响核心处理逻辑。
 * </p>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Slf4j
public class LoggingDecorator extends AgentStreamDecorator {

    private final Long conversationId;

    public LoggingDecorator(AgentStreamProcessor delegate, Long conversationId) {
        super(delegate);
        this.conversationId = conversationId;
    }

    @Override
    public Flux<AgentStreamEvent> process(Flux<NodeOutput> rawOutputFlux) {
        return delegate.process(rawOutputFlux)
                .doOnNext(this::logEvent)
                .doOnComplete(() -> log.info("流式处理流程结束，会话ID：{}", conversationId));
    }

    private void logEvent(AgentStreamEvent event) {
        if (event instanceof AgentStreamEvent.ModelContentChunk chunk) {
            log.debug("文本对话-[模型流式输出] 会话ID：{}，长度：{}", conversationId, chunk.text().length());
        } else if (event instanceof AgentStreamEvent.ModelThinkChunk chunk) {
            log.debug("文本对话-[模型思考输出] 会话ID：{}，长度：{}", conversationId, chunk.reasoning().length());
        } else if (event instanceof AgentStreamEvent.ModelComplete complete) {
            log.info("文本对话-模型流式输出完成，会话ID：{}，内容长度：{}",
                    conversationId, complete.message().getText().length());
        } else if (event instanceof AgentStreamEvent.ModelToolCall toolCall) {
            toolCall.message().getToolCalls().forEach(tc ->
                    log.debug("文本对话-[Tool Call] {} : {}", tc.name(), tc.arguments()));
        } else if (event instanceof AgentStreamEvent.ToolResponseReceived resp) {
            resp.message().getResponses().forEach(r ->
                    log.debug("文本对话-[Tool Response] {} : {}", r.name(), r.responseData()));
        } else if (event instanceof AgentStreamEvent.StreamError err) {
            log.error("文本对话-流式处理错误，会话ID：{}，错误：{}", conversationId, err.throwable().getMessage(), err.throwable());
        } else if (event instanceof AgentStreamEvent.FullThinkCompleted completed) {
            log.debug("文本对话-思考内容聚合完成，会话ID：{}，长度：{}", conversationId, completed.fullText().length());
        }
    }
}