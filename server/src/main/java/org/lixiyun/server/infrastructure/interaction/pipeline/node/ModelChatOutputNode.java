package org.lixiyun.server.infrastructure.interaction.pipeline.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.ai.model.processor.api.AgentStreamProcessor;
import org.lixiyun.server.ai.model.processor.api.StreamEventListener;
import org.lixiyun.server.ai.model.processor.factory.AgentStreamProcessorBuilder;
import org.lixiyun.server.constant.ConversationCacheConstant;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.infrastructure.conversation.ConversationMessageProcessor;
import org.lixiyun.server.infrastructure.conversation.ConversationStreamHolder;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
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
import reactor.core.scheduler.Schedulers;

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
    public static final String FLUX_KEY = "model_output";

    /** 源节点：任何输出组合都必须存在，servesType() 返回 null（见接口契约） */
    @Override
    public OutputDataType servesType() {
        return null;
    }

    /** 源节点恒在链首，排序置为 0 */
    @Override
    public int order() {
        return 0;
    }

    /** 链尾标记：由 OutputPipelineFactory 装配时注入（纯 TEXT 模式时本节点为链尾需自行订阅触发） */
    private boolean terminal;

    @Override
    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    private final ConversationStreamHolder conversationStreamHolder;
    private final ConversationMessageProcessor conversationMessageProcessor;
    private final ConversationWebSocketManager conversationWebSocketManager;
    private final ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;
    private final ConversationCacheManager conversationCacheManager;

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
        Flux<NodeOutput> outputFlux = conversationMessageProcessor.processConversationMessage(context);

        Integer currentRound = context.getCurrentRound();
        AgentStreamProcessor processor = AgentStreamProcessorBuilder.create()
                .withThinkAccumulate()
                .withLogging(conversationId)
                .withPersistence(conversationHistoryMessagesStorage, userId, conversationId, currentRound)
                .withListener(buildListener(userId, conversationId, currentRound, context))
                .build();

        // 存入 OutputContext（内部自动 .cache()），供下游节点消费
        context.putFlux(FLUX_KEY, outputFlux);
        Flux<NodeOutput> cachedFlux = context.getFlux(FLUX_KEY, NodeOutput.class);

        Flux<AgentStreamEvent> processedFlux = processor.process(cachedFlux)
                .subscribeOn(Schedulers.boundedElastic());

        context.putFlux(NODE_NAME, processedFlux);

        // 纯文本模式（TEXT 单输出）时本节点为链尾，需自行订阅触发整个链路
        if (terminal) {
            // 从 OutputContext 取回 cached 版本订阅，确保与下游共享同一份缓存，避免重复触发 LLM
            Flux<AgentStreamEvent> cachedFlux1 = context.getFlux(NODE_NAME, AgentStreamEvent.class);
            Disposable subscribe = cachedFlux1.subscribe();
            conversationStreamHolder.addStream(conversationId, subscribe);
            log.debug("[管道-文本输出节点] 流式订阅已注册到StreamHolder，会话ID：{}", conversationId);
        }
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

    /**
     * 构建流式事件监听器，处理内容分块、模型完成、异常和结束事件
     *
     * <p>输出副作用由 OutputContext.outputTypes 驱动（TTS 已解耦至 TtsOutputNode）：
     * <ul>
     *   <li>{@code TEXT}：每个文本分片经 /text/reply 推送</li>
     *   <li>{@code AUDIO}：由下游 {@code TtsOutputNode} 从 OutputContext.fluxStore 获取 Flux 后级联驱动</li>
     * </ul>
     * </p>
     *
     * @param userId          用户ID
     * @param conversationId  会话ID
     * @param currentRound    当前轮次
     * @param outputContext   输出上下文（含前端请求的输出模式集合）
     * @return 流式事件监听器
     */
    private StreamEventListener buildListener(Long userId, Long conversationId, int currentRound, OutputContext outputContext) {
        final StringBuilder contentBuilder = new StringBuilder();
        log.debug("[AI对话处理器] 构建流式监听器，用户ID：{}，会话ID：{}，轮次：{}", userId, conversationId, currentRound);

        boolean textOutput = outputContext != null && outputContext.hasOutputType(OutputDataType.TEXT);
        log.debug("[AI对话处理器] 输出模式判断结果，会话ID：{}，textOutput={}", conversationId, textOutput);

        return new StreamEventListener() {
            @Override
            public void onContentChunk(String text) {
                log.debug("[AI对话处理器] 收到内容分块，长度：{}，累计长度：{}，会话ID：{}", text != null ? text.length() : 0, contentBuilder.length(), conversationId);
                contentBuilder.append(text);
                if (textOutput) {
                    conversationWebSocketManager.sendTextStream(userId, conversationId, text);
                }
            }

            @Override
            public void onInterrupted() {
                log.info("AI对话语音处理器-检测到流中断，保存已输出部分内容，会话ID：{}", conversationId);
                ConversationMemory finalMemory = ConversationMemory.builder()
                        .userId(userId)
                        .conversationId(conversationId)
                        .content(contentBuilder + "\n[系统信息：用户进行了中断]")
                        .type(MessageType.ASSISTANT.getName())
                        .state(ConversationMemory.STATE_PROCESSED)
                        .roundNum(currentRound)
                        .build();
                updateCacheHistory(conversationId, finalMemory);
            }

            @Override
            public void onModelComplete(org.springframework.ai.chat.messages.AssistantMessage message) {
                log.info("AI对话语音处理器-流式完成，会话ID：{}", conversationId);
                log.debug("[AI对话处理器] 模型输出完成，内容长度：{}，会话ID：{}", message.getText() != null ? message.getText().length() : 0, conversationId);

                String textContent = message.getText();

                ConversationMemory finalMemory = ConversationMemory.builder()
                        .userId(userId)
                        .conversationId(conversationId)
                        .content(textContent)
                        .type(MessageType.ASSISTANT.getName())
                        .state(ConversationMemory.STATE_PROCESSED)
                        .roundNum(currentRound)
                        .build();

                updateCacheHistory(conversationId, finalMemory);
            }

            @Override
            public void onError(Throwable err) {
                log.error("AI对话语音处理器-流式处理异常，会话ID：{}，错误：{}", conversationId, err.getMessage(), err);
            }

            @Override
            public void onFinished() {
                conversationStreamHolder.removeStream(conversationId);
                log.info("AI对话语音处理器-流程结束，会话ID：{}", conversationId);
            }
        };
    }

    /**
     * 更新Redis缓存中的会话历史上下文，将新消息追加到已有历史列表
     *
     * @param conversationId     会话ID
     * @param conversationMemory 待追加的会话记忆
     */
    private void updateCacheHistory(Long conversationId, ConversationMemory conversationMemory) {
        log.info("AI对话语音处理器-更新Redis缓存历史上下文，会话ID：{}", conversationId);

        try {
            List<ConversationMemory> existingHistory = (List<ConversationMemory>) conversationCacheManager.getCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES);
            log.debug("[AI对话处理器] 获取现有历史消息数：{}，会话ID：{}", existingHistory != null ? existingHistory.size() : 0, conversationId);

            existingHistory.add(conversationMemory);

            conversationCacheManager.updateCacheMapValue(conversationId, ConversationCacheConstant.HASH_FIELD_HISTORY_MESSAGES, existingHistory);

            log.info("AI对话语音处理器-缓存历史上下文更新成功，当前消息数：{}，会话ID：{}", existingHistory.size(), conversationId);
        } catch (Exception e) {
            log.error("AI对话语音处理器-更新缓存历史上下文失败（不影响主流程），会话ID：{}，错误：{}", conversationId, e.getMessage(), e);
        }
    }


}