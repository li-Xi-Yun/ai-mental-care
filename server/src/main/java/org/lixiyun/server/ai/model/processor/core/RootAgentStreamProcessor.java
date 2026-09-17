package org.lixiyun.server.ai.model.processor.core;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import reactor.core.publisher.Flux;

/**
 * 裸基础处理器 - 唯一直接解析 SDK 原始数据的处理器
 * <p>
 * 职责：将 {@code NodeOutput / StreamingOutput} 翻译为 {@link AgentStreamEvent} 领域事件。
 * 不做持久化、不拼接思考文本、不触发业务回调，仅做纯领域转换。
 * </p>
 *
 * <h3>可配置项：</h3>
 * <ul>
 *     <li>{@code reasoningMetaKey} - 思考内容元数据 Key，不同模型厂商可能使用不同的 Key 名</li>
 * </ul>
 *
 * <h3>各厂商默认 Key：</h3>
 * <ul>
 *     <li>DeepSeek / Ollama(qwen3-thinking) → {@code reasoningContent}</li>
 *     <li>DashScope(qwen-max) → {@code thinking}</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Slf4j
public class RootAgentStreamProcessor implements AgentStreamProcessor {

    private final String reasoningMetaKey;

    public RootAgentStreamProcessor(String reasoningMetaKey) {
        this.reasoningMetaKey = reasoningMetaKey;
    }

    public RootAgentStreamProcessor() {
        this("reasoningContent");
    }

    @Override
    public Flux<AgentStreamEvent> process(Flux<NodeOutput> rawOutputFlux) {
        return rawOutputFlux
                .mapNotNull(this::convertOutputToEvent)
                .onErrorResume(e -> Flux.just(new AgentStreamEvent.StreamError(e)))
                .concatWithValues(new AgentStreamEvent.StreamFinished());
    }

    private AgentStreamEvent convertOutputToEvent(NodeOutput output) {
        if (!(output instanceof StreamingOutput streamingOutput)) {
            // 到这里说明是普通的NodeOutput类型
            return null;
        }

        OutputType type = streamingOutput.getOutputType();
        Message message = streamingOutput.message();

        if (type == OutputType.AGENT_MODEL_STREAMING) {
            if (message instanceof AssistantMessage assistantMessage) {
                Object reasoning = assistantMessage.getMetadata().get(reasoningMetaKey);
                if (reasoning != null && !reasoning.toString().isBlank()) {
                    return new AgentStreamEvent.ModelThinkChunk(reasoning.toString());
                }
                String text = assistantMessage.getText();
                if (text != null && !text.isBlank()) {
                    return new AgentStreamEvent.ModelContentChunk(text);
                }
                return null;
            }
            return null;
        }

        if (type == OutputType.AGENT_MODEL_FINISHED) {
            if (message instanceof AssistantMessage assistantMessage) {
                if (assistantMessage.hasToolCalls()) {
                    return new AgentStreamEvent.ModelToolCall(assistantMessage);
                }
                return new AgentStreamEvent.ModelComplete(assistantMessage);
            }
            return null;
        }

        if (type == OutputType.AGENT_TOOL_FINISHED) {
            if (message instanceof ToolResponseMessage toolMsg) {
                return new AgentStreamEvent.ToolResponseReceived(toolMsg);
            }
            return null;
        }

        if (type == OutputType.AGENT_HOOK_FINISHED) {
            // 对于 Hook 节点，通常只关注完成事件（如果Hook没有有效输出可以忽略）
            log.debug("Hook节点执行完成: {}", output.node());
            return null;
        }

        log.debug("未识别OutputType: {}", type);
        return null;
    }

}