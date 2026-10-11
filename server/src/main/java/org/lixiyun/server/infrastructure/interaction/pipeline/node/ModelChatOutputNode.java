package org.lixiyun.server.infrastructure.interaction.pipeline.node;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.infrastructure.conversation.ConversationMessageProcessor;
import org.lixiyun.server.infrastructure.conversation.ConversationStreamHolder;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputContext;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputDataType;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputNode;
import org.lixiyun.server.socket.constant.ConversationConstant;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 大语言模型输出节点
 *
 * <p><b>职责</b>：管道触发的入口节点——调用流程编排器 {@link ConversationMessageProcessor} 获取模型输出的冷 Flux，
 * 存入 {@link OutputContext#putFlux}（自动 .cache()）供下游节点消费，并在本节点完成首次订阅以触发整个 LLM 链路。</p>
 *
 * <p><b>触发路径（TTS 已解耦至 TtsOutputNode）</b>：</p>
 * <pre>
 * [ModelChatOutputNode].process(context)
 *     ├── Flux&lt;AgentStreamEvent&gt; flux = conversationMessageProcessor.processConversationMessage(context)
 *     │   ├── 回填 outputTypes（缺失时取管道绑定值/默认{TEXT}）
 *     │   ├── 令牌 → 缓存 → 未处理消息 → 上下文BO
 *     │   └── [ChatMessageProcessor].processMessage(processContext, outputContext) → 返回冷 Flux
 *     │       ├── buildPrompt → AgentStreamProcessor 装饰链（含 TEXT 监听器）
 *     │       └── 返回 processor.process(stream).subscribeOn(boundedElastic)
 *     ├── context.putFlux("model_output", flux)  ← 存入管道上下文（自动 .cache()）
 *     └── cachedFlux.subscribe() → conversationStreamHolder.addStream()  ← 本节点订阅触发链路
 *
 * [TtsOutputNode].process(context)
 *     └── context.getFlux("model_output") → doOnNext(TTS) → subscribe()  ← 下游附加副作用
 * </pre>
 *
 * <h3>节点实例化约定：</h3>
 * <p>本节点持有会话级状态（conversationId / userId），被声明为 <b>prototype</b> 作用域，
 * {@code OutputPipelineFactory} 在组装管道时为每个会话创建独立实例（ObjectProvider.getObject()）。</p>
 *
 * @author lixiyun
 * @since 2026-10-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ModelChatOutputNode implements OutputNode {

    /** 节点名称 */
    public static final String NODE_NAME = "modelChatOutputNode";

    private final ConversationStreamHolder conversationStreamHolder;
    private final ConversationMessageProcessor conversationMessageProcessor;

    /** 会话级状态：由 init() 注入，节点实例与单一会话绑定（prototype） */
    private Long conversationId;
    private Long userId;

    // ==================== 元信息 ====================

    @Override
    public String getNodeName() {
        return NODE_NAME;
    }

    // ==================== 生命周期 ====================

    @Override
    public void init(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
        log.info("[管道-文本输出节点] 节点初始化，会话ID={}，用户ID={}", conversationId, userId);
    }

    @Override
    public void destroy() {
        conversationStreamHolder.cancelStream(conversationId);
        log.info("[管道-文本输出节点] 节点销毁，会话ID：{}", conversationId);
    }

    @Override
    public void interrupt() {
        conversationStreamHolder.cancelStream(conversationId);
        log.info("[管道-文本输出节点] 节点中断，会话ID：{}", conversationId);
    }

    // ==================== 核心处理 ====================

    /**
     * 调用流程编排器获取模型输出的冷 Flux，存入 {@link OutputContext}（自动 .cache()），
     * 并完成首次订阅以触发 LLM + TEXT 推送链路。下游节点（如 {@code TtsOutputNode}）
     * 可从 context 中获取同一 cached Flux 实例附加额外副作用。
     *
     * @param context 输出上下文
     */
    @Override
    public void process(OutputContext context) {
        Flux<AgentStreamEvent> outputFlux = conversationMessageProcessor.processConversationMessage(context);

        // 存入 OutputContext（内部自动 .cache()），供下游节点（TtsOutputNode）消费
        context.putFlux(NODE_NAME, outputFlux);

        if (!context.hasOutputType(OutputDataType.TEXT)) {
            return;
        }

        // 从 OutputContext 取回 cached 版本订阅，确保与下游共享同一份缓存，避免重复触发 LLM
        Flux<AgentStreamEvent> cachedFlux = context.getFlux(NODE_NAME, AgentStreamEvent.class);
        Disposable subscribe = cachedFlux.subscribe();
        conversationStreamHolder.addStream(conversationId, subscribe);
        log.debug("[管道-文本输出节点] 流式订阅已注册到StreamHolder，会话ID：{}", conversationId);
    }

    // ==================== Socket 路径与说明 ====================

    @Override
    public List<NodeEndpoint> getSocketInfo() {
        return List.of(
                NodeEndpoint.builder()
                        .description("AI文本流式回复（客户端需拼接/{conversationId}）")
                        .path(ConversationConstant.AI_TEXT_REPLY)
                        .build(),
                NodeEndpoint.builder()
                        .description("会话名称，客户端订阅时，需要在后面路径中添加/{conversationId}")
                        .path(ConversationConstant.CONVERSATION_NAME)
                        .build(),
                NodeEndpoint.builder()
                        .description("待处理交互推送，需要在后面路径中添加/{conversationId}")
                        .path(ConversationConstant.PENDING_ACTION)
                        .build()
        );
    }
}