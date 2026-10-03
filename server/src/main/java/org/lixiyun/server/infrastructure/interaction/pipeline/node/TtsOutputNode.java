package org.lixiyun.server.infrastructure.interaction.pipeline.node;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.tts.api.TtsResultCallback;
import org.lixiyun.server.infrastructure.audio.TtsConnectionManager;
import org.lixiyun.server.infrastructure.conversation.ConversationCacheManager;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputContext;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputNode;
import org.lixiyun.server.socket.constant.ConversationConstant;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文本转语音输出节点
 *
 * <p><b>职责（方案A）</b>：作为 TTS 合成资源的<b>持有者</b>，TTS 合成逻辑由统一处理器
 * {@code ChatMessageProcessor} 在内部级联驱动（listener 中按 OutputContext.hasOutputType(AUDIO) 判断）。</p>
 * <ul>
 *   <li><b>init()</b>：注册 TTS 连接与音频推送回调（onAudioData → 音频二进制 WebSocket 推送）</li>
 *   <li><b>process()</b>：空实现——本节点不再消费上游文本流，合成由处理器级联</li>
 *   <li><b>destroy()</b>：取消 TTS 会话、清理资源</li>
 *   <li><b>interrupt()</b>：中断当前合成，保留会话可再次合成</li>
 * </ul>
 *
 * <p><b>输出副作用</b>：
 * <ul>
 *   <li>音频二进制（/audio/binary）：由注册的 TTS 回调 {@code onAudioData} 推送</li>
 *   <li>文字字幕（/audio/reply）：由 ChatMessageProcessor 在 outputTypes 含 AUDIO 时推送</li>
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

    private final TtsConnectionManager ttsConnectionManager;
    private final ConversationWebSocketManager conversationWebSocketManager;
    private final ConversationCacheManager conversationCacheManager;

    /** 会话级状态：由 init() 注入，节点实例与单一会话绑定（prototype） */
    private Long conversationId;
    private Long userId;

    // ==================== 元信息 ====================

    @Override
    public String getNodeName() {
        return "TtsNode";
    }

    // ==================== 生命周期 ====================

    @Override
    public void init(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
        // 注册 TTS 连接与音频推送回调（TtsConnectionManager 为一用户一会话：重复注册会自动关闭旧会话）
        ttsConnectionManager.register(userId, createTtsCallback(conversationId, userId));
        log.info("[TtsNode] 节点初始化完成，会话ID={}，用户ID={}，已注册TTS连接", conversationId, userId);
    }

    @Override
    public void destroy() {
        ttsConnectionManager.cancel(userId);
        log.info("[TtsNode] 节点销毁，会话ID={}，用户ID={}", conversationId, userId);
    }

    @Override
    public void interrupt() {
        ttsConnectionManager.interrupt(userId);
        log.info("[TtsNode] 节点中断，会话ID={}，用户ID={}", conversationId, userId);
    }

    // ==================== 核心处理 ====================

    /**
     * 方案A——空实现
     *
     * <p>TTS 合成由统一处理器 {@code ChatMessageProcessor} 在内部级联驱动
     * （listener 中根据 OutputContext.hasOutputType(AUDIO) 判断是否调用 TtsConnectionManager），
     * 本节点不再消费上游文本流，仅作为 TTS 连接的资源持有者：</p>
     * <ul>
     *   <li>init()：注册 TTS 连接与音频推送回调</li>
     *   <li>destroy() / interrupt()：取消 / 中断 TTS 会话</li>
     * </ul>
     *
     * @param context 输出上下文（本节点不消费，忽略）
     */
    @Override
    public void process(OutputContext context) {
        // TTS 合成由 ChatMessageProcessor 内级联驱动，本节点无处理逻辑
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