package org.lixiyun.server.infrastructure.interaction.pipeline.node;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.tts.api.TtsResultCallback;
import org.lixiyun.pojo.entity.quota.QuotaConfig;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.infrastructure.ai.annotation.QuotaLimit;
import org.lixiyun.server.infrastructure.audio.TtsConnectionManager;
import org.lixiyun.server.infrastructure.conversation.ConversationStreamHolder;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputContext;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputDataType;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputNode;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputPipelineFactory;
import org.lixiyun.server.socket.constant.ConversationConstant;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 文本转语音输出节点
 *
 * <p><b>职责</b>：从 {@link OutputContext#getFlux} 中获取上游 {@code ModelChatOutputNode} 产出的
 * {@code Flux<AgentStreamEvent>}，在 {@link #process(OutputContext)} 中对 Flux 附加 TTS 合成副作用
 * （{@code doOnNext} → 文本分片送入引擎，{@code doOnComplete} → 通知合成收尾），并执行订阅触发整个链路。</p>
 * <ul>
 *   <li><b>init()</b>：注册 TTS 连接与音频推送回调（onAudioData → 音频二进制 WebSocket 推送）</li>
 *   <li><b>process()</b>：从 OutputContext 获取 cached Flux，附加 TTS 逻辑后订阅</li>
 *   <li><b>destroy()</b>：取消 TTS 会话、清理资源</li>
 *   <li><b>interrupt()</b>：中断当前合成，保留会话可再次合成</li>
 * </ul>
 *
 * <p><b>输出副作用</b>：
 * <ul>
 *   <li>音频二进制（/audio/binary）：由注册的 TTS 回调 {@code onAudioData} 推送</li>
 *   <li>文字字幕（/audio/reply）：由 ChatMessageProcessor 的 TEXT 监听器在 outputTypes 含 TEXT 时推送</li>
 * </ul></p>
 *
 * <h3>节点实例化约定：</h3>
 * <p>与 {@link ModelChatOutputNode} 一致，持有会话级状态（conversationId / userId），声明为 <b>prototype</b> 作用域，
 * {@code OutputPipelineFactory} 组装时应为每个会话创建独立实例。</p>
 *
 * @author lixiyun
 * @since 2026-10-03
 */
@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class TtsOutputNode implements OutputNode {

    /** 节点名称 */
    public static final String NODE_NAME = "ttsOutputNode";

    /** 本节点负责的输出类型，由声明式 Factory 按 outputTypes 过滤装配 */
    @Override
    public OutputDataType servesType() {
        return OutputDataType.AUDIO;
    }

    /** TTS 位于链末，消费上游产出 */
    @Override
    public int order() {
        return 20;
    }

    /** Factory 注入的上游 Flux key（含 AVATAR_2D 时为 AvatarOutputNode.NODE_NAME，否则为 ModelChatOutputNode.NODE_NAME） */
    private String inputFluxKey;

    @Override
    public void setInputFluxKey(String inputFluxKey) {
        this.inputFluxKey = inputFluxKey;
    }

    private final TtsConnectionManager ttsConnectionManager;
    private final ConversationWebSocketManager conversationWebSocketManager;
    private final ConversationStreamHolder conversationStreamHolder;

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
        // 注册 TTS 连接与音频推送回调（TtsConnectionManager 为一用户一会话：重复注册会自动关闭旧会话）
        ttsConnectionManager.register(userId, createTtsCallback(conversationId, userId));
        log.info("[管道-音频输出节点] 节点初始化完成，会话ID={}，用户ID={}，已注册TTS连接", conversationId, userId);
    }

    @Override
    public void destroy() {
        ttsConnectionManager.cancel(userId);
        log.info("[管道-音频输出节点] 节点销毁，会话ID={}，用户ID={}", conversationId, userId);
    }

    @Override
    public void interrupt() {
        ttsConnectionManager.interrupt(userId);
        log.info("[管道-音频输出节点] 节点中断，会话ID={}，用户ID={}", conversationId, userId);
    }

    // ==================== 核心处理 ====================

    /**
     * 从 {@link OutputContext} 获取上游产出的 cached {@code Flux<AgentStreamEvent>}，
     * 附加 TTS 合成副作用后订阅，触发整个 LLM + TTS 链路。
     *
     * <p>上游产出 key 由 {@link OutputPipelineFactory} 声明式装配时注入
     * （{@link #setInputFluxKey}）：含 AVATAR_2D 时为 {@code AvatarOutputNode} 重包装后的正文 Flux
     * （已剥离控制段），否则为 {@code ModelChatOutputNode} 原始输出。</p>
     *
     * <p>由于 {@link OutputContext#putFlux} 已对 Flux 执行 {@code .cache()}，
     * 本节点的订阅是同一 cached Flux 的二次订阅，不会重复触发 LLM 数据源，
     * 而是重放已缓存的事件并继续接收实时事件。</p>
     *
     * <p><b>AI配额（切面拦截）</b>：被拒时——纯 AUDIO 模式整节点被切面跳过（本轮无回复仅拒绝事件）；
     * 混合模式切面注入 {@code quota:skip} 标记后放行，本方法读标记降级为纯文本订阅
     * （不附加 TTS 副作用，TEXT 通道照常送达）。</p>
     *
     * @param context 输出上下文
     */
    @Override
    @QuotaLimit(resourceType = QuotaConfig.RESOURCE_TYPE_TTS,
                userSource = QuotaLimit.QuotaUserSource.OUTPUT_CONTEXT,
                async = true)
    public void process(OutputContext context) {
        boolean audioOutput = context.hasOutputType(OutputDataType.AUDIO);
        if (!audioOutput) {
            log.debug("[TtsNode] 非音频模式，跳过TTS处理，会话ID：{}", conversationId);
            return;
        }

        if (Boolean.TRUE.equals(context.getData("quota:skip:" + QuotaConfig.RESOURCE_TYPE_TTS, Boolean.class))) {
            // 配额被拒（混合模式降级为纯文本）：驱动整链保证 TEXT 通道送达，不附加 TTS 副作用
            Flux<AgentStreamEvent> cachedFlux = context.getFlux(inputFluxKey, AgentStreamEvent.class);
            Disposable textOnlySubscribe = cachedFlux.subscribe();
            conversationStreamHolder.addStream(conversationId, textOnlySubscribe);
            log.info("[TtsNode] 配额被拒，降级为纯文本，会话ID：{}", conversationId);
            return;
        }

        Flux<AgentStreamEvent> cachedFlux = context.getFlux(inputFluxKey, AgentStreamEvent.class);

        Disposable ttsSubscribe = cachedFlux
                .doOnNext(event -> {
                    if (event instanceof AgentStreamEvent.ModelContentChunk(String text)) {
                        // 防御性降级：TTS 异常不得终止本订阅（否则音频全丢且抛出未处理异常）
                        try {
                            ttsConnectionManager.sendTextSegment(userId, text);
                        } catch (Exception e) {
                            log.warn("[管道-音频输出节点] TTS分片发送异常，本次降级为纯文本，会话ID：{}，原因：{}",
                                    conversationId, e.getMessage());
                        }
                    }
                })
                .doOnComplete(() -> {
                    try {
                        ttsConnectionManager.finishSynthesis(userId);
                    } catch (Exception e) {
                        log.warn("[管道-音频输出节点] TTS收尾异常（忽略），会话ID：{}，原因：{}", conversationId, e.getMessage());
                    }
                    log.debug("[管道-音频输出节点] TTS合成完成，会话ID：{}", conversationId);
                })
                .doOnError(err -> {
                    log.error("[管道-音频输出节点] TTS流处理异常，会话ID：{}", conversationId, err);
                })
                .subscribe();

        conversationStreamHolder.addStream(conversationId, ttsSubscribe);

        log.debug("[管道-音频输出节点] TTS流订阅完成，会话ID={}，音频输出已激活", conversationId);
    }

    /**
     * 创建 TTS 合成结果回调：音频数据推送前端 / 合成结束清理处理标识。
     */
    private TtsResultCallback createTtsCallback(Long conversationId, Long userId) {
        return new TtsResultCallback() {
            @Override
            public void onAudioData(byte[] audioData) {
                log.debug("[TtsNode] TTS回调-音频数据推送，会话ID：{}，数据长度：{}字节", conversationId, audioData.length);
                conversationWebSocketManager.sendAudioBinary(userId, conversationId, audioData);
            }

            @Override
            public void onSynthesisComplete() {
                log.debug("[TtsNode] TTS合成完成，已清理语音处理标识，会话ID：{}", conversationId);
            }

            @Override
            public void onFail(String taskId, String statusText) {
                log.error("[TtsNode] TTS合成失败，会话ID：{}，任务ID：{}，错误：{}", conversationId, taskId, statusText);
            }
        };
    }

    // ==================== Socket 路径与说明 ====================

    @Override
    public List<NodeEndpoint> getSocketInfo() {
        return List.of(
                NodeEndpoint.builder()
                        .description("AI音频二进制数据推送（客户端需拼接/{conversationId}）")
                        .path(ConversationConstant.AI_AUDIO_BINARY)
                        .build()
        );
    }
}