package org.lixiyun.server.infrastructure.interaction.pipeline.node;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.infrastructure.conversation.ConversationMessageProcessor;
import org.lixiyun.server.infrastructure.conversation.ConversationStreamHolder;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputContext;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputNode;
import org.lixiyun.server.socket.constant.ConversationConstant;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 大语言模型输出节点
 *
 * <p><b>职责</b>：作为管道触发的入口节点——将 {@link OutputContext} 透传给流程编排器
 * {@link ConversationMessageProcessor}，由 ChatMessageProcessor 统一执行 LLM 流式推理，
 * 并根据 context.outputTypes 判断 TEXT/AUDIO 的推送副作用（方案A，TTS 在处理器内部级联）。</p>
 *
 * <p><b>触发路径</b>：</p>
 * <pre>
 * [ModelChatOutputNode].process(context)
 *     └── conversationMessageProcessor.processConversationMessage(context)
 *         ├── 回填 outputTypes（缺失时取管道绑定值/默认{TEXT}）
 *         ├── 令牌 → 缓存 → 未处理消息 → 上下文BO
 *         └── [ChatMessageProcessor].processMessage(processContext, outputContext)
 *             ├── buildPrompt → AgentStreamProcessor 装饰链 → LLM 流式推理
 *             ├── TEXT：onContentChunk → /text/reply 推送
 *             └── AUDIO：onContentChunk → TTS 合成 + /audio/reply 字幕，onComplete → finishSynthesis
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
    public static final String NODE_NAME = "modelNode";

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
        log.info("[ModelChatOutputNode] 节点初始化，会话ID={}，用户ID={}", conversationId, userId);
    }

    @Override
    public void destroy() {
        conversationStreamHolder.cancelStream(conversationId);
        log.info("[ModelChatOutputNode] 节点销毁，会话ID：{}", conversationId);
    }

    @Override
    public void interrupt() {
        conversationStreamHolder.cancelStream(conversationId);
        log.info("[ModelChatOutputNode] 节点中断，会话ID：{}", conversationId);
    }

    // ==================== 核心处理 ====================

    @Override
    public void process(OutputContext context) {
        conversationMessageProcessor.processConversationMessage(context);
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
                        .build()
        );
    }
}
