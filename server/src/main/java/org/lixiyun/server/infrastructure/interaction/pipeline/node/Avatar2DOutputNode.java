package org.lixiyun.server.infrastructure.interaction.pipeline.node;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.Live2dConfigExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.entity.config.Live2dConfig;
import org.lixiyun.pojo.entity.quota.QuotaConfig;
import org.lixiyun.server.ai.model.pipeline.AvatarModel;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.ai.node.pipeline.AvatarNode;
import org.lixiyun.server.infrastructure.ai.annotation.QuotaLimit;
import org.lixiyun.server.infrastructure.conversation.ConversationStreamHolder;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputContext;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputDataType;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputNode;
import org.lixiyun.server.mapper.Live2dConfigMapper;
import org.lixiyun.server.socket.constant.AvatarConstant;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 虚拟形象（Live2D）输出节点
 *
 * <p><b>职责</b>：分离结构化 JSON 方案下的中间节点，处于
 * {@code TEXT → AVATAR_2D → TTS} 节点链的中间位置：</p>
 * <ul>
 *   <li>主对话模型在分离方案下<b>只输出纯正文</b>（不再携带控制段 JSON）；
 *       本节点从 {@link OutputContext} 获取上游 {@code ModelChatOutputNode} 产出的 cached
 *       {@code Flux<AgentStreamEvent>}（key 为 {@link ModelChatOutputNode#NODE_NAME}）</li>
 *   <li>在事件流上收集 {@code ModelComplete} 的完整模型文本，流完成后<b>异步</b>调用独立
 *       {@link AvatarNode}（→ {@link AvatarModel}）生成结构化动作/表情指令 → 白名单校验 →
 *       经 WebSocket 下发 {@code /avatar/expression} / {@code /avatar/motion}</li>
 *   <li>正文流<b>直接透传</b>为节点产出（key 为 {@link Avatar2DOutputNode#NODE_NAME}）写入
 *       {@link OutputContext}：含 `AUDIO` 输出时由 {@code TtsOutputNode} 消费，
 *       不含 `AUDIO` 时本节点作为链尾自行订阅触发</li>
 * </ul>
 *
 * <p><b>关键设计</b>：
 * <ul>
 *   <li><b>零双触发</b>：仅以 doOnNext/doOnComplete 附加副作用，不单独订阅上游数据源，
 *       整链由链尾唯一订阅驱动（共享 .cache() 实例），正文与表情/动作通道各自独立</li>
 *   <li><b>异步 LLM 调用</b>：表情/动作生成挂 {@code Schedulers.boundedElastic()}，不阻塞正文完成信号</li>
 *   <li><b>异常隔离</b>：生成/校验/推送任何异常仅影响表情/动作，不影响正文与 TTS 通道</li>
 *   <li><b>透传而非剥离</b>：主对话流已不携带控制段，无需剥离逻辑</li>
 * </ul>
 * </p>
 *
 * <h3>节点实例化约定</h3>
 * <p>与 {@link ModelChatOutputNode} / {@link TtsOutputNode} 一致，持有会话级状态
 * （conversationId / userId），声明为 <b>prototype</b> 作用域，由
 * {@code OutputPipelineFactory} 每个会话创建独立实例，且必须排在 {@code TtsOutputNode} 之前。</p>
 *
 * @author lixiyun
 * @since 2026-10-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Avatar2DOutputNode implements OutputNode {

    /** 节点名称 */
    public static final String NODE_NAME = "avatarOutputNode";

    /** 本节点负责的输出类型，由声明式 Factory 按 outputTypes 过滤装配 */
    @Override
    public OutputDataType servesType() {
        return OutputDataType.AVATAR_2D;
    }

    /** 控制段剥离必须发生在 TTS 之前 */
    @Override
    public int order() {
        return 10;
    }

    /** Factory 注入的上游 Flux key（通常为 ModelChatOutputNode.NODE_NAME） */
    private String inputFluxKey;

    /** Factory 注入的链尾标记：无 AUDIO 时为链尾 */
    private boolean terminal;

    @Override
    public void setInputFluxKey(String inputFluxKey) {
        this.inputFluxKey = inputFluxKey;
    }

    @Override
    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    /** 控制段终止分隔符：首个 "+++" 之后均为正文 */
    private static final String CONTROL_TERMINATOR = "+++";

    /** 控制段最大长度保护（字符），超过仍未出现 "+++" 则放弃控制段、直接按正文放行 */
    private static final int MAX_CONTROL_LENGTH = 100;

    private final ConversationStreamHolder conversationStreamHolder;
    private final ConversationWebSocketManager conversationWebSocketManager;
    private final AvatarNode avatarNode;
    private final Live2dConfigMapper live2dConfigMapper;

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
        Live2dConfig enabledConfig = live2dConfigMapper.selectOne(
                new LambdaQueryWrapper<Live2dConfig>()
                        .eq(Live2dConfig::getEnabled, Live2dConfig.ENABLED_YES)
                        .orderByAsc(Live2dConfig::getSort)
                        .last("limit 1")
        );
        if (enabledConfig == null) {
            log.warn("[管道-虚拟形象输出节点] Live2D功能未启用，拒绝初始化，会话ID={}，用户ID={}",
                    conversationId, userId);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_FEATURE_NOT_ENABLED);
        }

        this.conversationId = conversationId;
        this.userId = userId;
        log.info("[管道-虚拟形象输出节点] 节点初始化完成，会话ID={}，用户ID={}，模型={}",
                conversationId, userId, enabledConfig.getModelName());
    }

    @Override
    public void destroy() {
        conversationStreamHolder.cancelStream(conversationId);
        log.info("[管道-虚拟形象输出节点] 节点销毁，会话ID={}，用户ID={}", conversationId, userId);
    }

    @Override
    public void interrupt() {
        conversationStreamHolder.cancelStream(conversationId);
        log.info("[管道-虚拟形象输出节点] 节点中断，会话ID={}，用户ID={}", conversationId, userId);
    }

    // ==================== 核心处理 ====================

    /**
     * 虚拟形象输出处理（分离结构化 JSON 方案）：
     * <ol>
     *   <li>从 {@link OutputContext} 获取上游 {@code ModelChatOutputNode} 产出的 cached
     *       {@code Flux<AgentStreamEvent>}（key 为 {@link ModelChatOutputNode#NODE_NAME}）</li>
     *   <li>在事件流上收集 {@link AgentStreamEvent.ModelComplete} 的完整模型文本（不订阅、不触发数据源），
     *       流完成（{@code doOnComplete}）后<b>异步</b>调用 {@link AvatarNode} 生成表情/动作指令并 WebSocket 下发，
     *       任何异常仅降级日志，不影响正文与 TTS 通道</li>
     *   <li>主对话在分离方案下输出纯正文，本节点<b>直接透传</b>上游 Flux 到下游
     *       （key 为 {@link #NODE_NAME}）：含 {@code AUDIO} 时由 {@code TtsOutputNode} 消费，
     *       不含 {@code AUDIO} 时本节点作为链尾自行订阅触发</li>
     * </ol>
     *
     * <p><b>关键设计</b>：
     * <ul>
     *   <li><b>零双触发</b>：仅通过 {@code doOnNext/doOnComplete} 附加副作用，不再单独订阅上游数据源，
     *       由链尾节点的唯一订阅驱动整链（共享 {@link OutputContext#putFlux} 的 .cache() 实例）</li>
     *   <li><b>异步 LLM 调用</b>：表情/动作生成挂到 {@code Schedulers.boundedElastic()}，
     *       不阻塞正文流完成信号</li>
     *   <li><b>异常隔离</b>：生成/校验/推送任何异常仅影响表情/动作，不会中断正文与 TTS 通道</li>
     *   <li><b>透传而非剥离</b>：主对话流已不携带控制段 JSON，无需剥离</li>
     * </ul>
     * </p>
     *
     * <p><b>AI配额（切面拦截）</b>：被拒时——纯 AVATAR_2D 模式整节点被切面跳过（本轮无回复仅拒绝事件）；
     * 混合模式切面注入 {@code quota:skip} 标记后放行，本方法读标记跳过表情/动作生成
     * （仍透传正文并驱动链，TEXT/TTS 通道照常送达）。</p>
     *
     * @param context 输出上下文
     */
    @Override
    @QuotaLimit(resourceType = QuotaConfig.RESOURCE_TYPE_AVATAR_2D,
                userSource = QuotaLimit.QuotaUserSource.OUTPUT_CONTEXT,
                async = true)
    public void process(OutputContext context) {
        boolean avatarOutput = context.hasOutputType(OutputDataType.AVATAR_2D);
        if (!avatarOutput) {
            log.debug("[管道-虚拟形象输出节点] 非虚拟形象输出模式，跳过处理，会话ID：{}", conversationId);
            return;
        }

        if (Boolean.TRUE.equals(context.getData("quota:skip:" + QuotaConfig.RESOURCE_TYPE_AVATAR_2D, Boolean.class))) {
            // 配额被拒（混合模式）：仍透传正文流并订阅驱动（TEXT/TTS 通道可送达），跳过表情/动作生成
            context.putFlux(NODE_NAME, context.getFlux(inputFluxKey, AgentStreamEvent.class));
            if (terminal) {
                Disposable subscribe = context.getFlux(inputFluxKey, AgentStreamEvent.class).subscribe();
                conversationStreamHolder.addStream(conversationId, subscribe);
            }
            log.info("[管道-虚拟形象输出节点] 配额被拒，跳过表情/动作生成，会话ID：{}", conversationId);
            return;
        }

        // 本轮用户输入（编排器已注入 OutputContext.dataStore）
        ConversationProcessContextBO processContext = context.getData(ConversationProcessContextBO.NAME, ConversationProcessContextBO.class);

        // 收集完整模型输出文本（ModelComplete 携带完整回复），流完成后异步生成表情/动作
        AtomicReference<String> modelText = new AtomicReference<>("");
        Flux<NodeOutput> nodeOutputFlux = context.getFlux(ModelChatOutputNode.FLUX_KEY, NodeOutput.class)
                .doOnNext(output -> {
                    if (!(output instanceof StreamingOutput streamingOutput)) {
                        // 到这里说明是普通的NodeOutput类型
                        return;
                    }

                    OutputType type = streamingOutput.getOutputType();
                    Message message = streamingOutput.message();

                    if (type == OutputType.AGENT_MODEL_FINISHED) {
                        if (message instanceof AssistantMessage assistantMessage) {
                            modelText.set(assistantMessage.getText());
                            log.debug("[管道-虚拟形象输出节点] 已捕获完整模型输出，长度：{}，会话ID：{}", modelText.get().length(), conversationId);
                        }
                    }
                });

        // 同步阻塞等待模型输出流全部完成，不再异步subscribe，超时10秒
        try {
            nodeOutputFlux.blockLast(Duration.ofSeconds(10));
        } catch (Exception e) {
            log.warn("[管道-虚拟形象输出节点] 模型输出流消费异常，会话ID：{}", conversationId, e);
        }

        try {
            AvatarModel.AvatarCommandPayload command = avatarNode.apply(modelText.get(), processContext);
            publishCommand(command);
        } catch (Exception e) {
            log.warn("[管道-虚拟形象输出节点] 表情/动作生成失败（不影响正文与TTS），会话ID：{}，原因：{}",
                    conversationId, e.getMessage(), e);
        }

        // 透传正文流到下游（TTS），由链尾唯一订阅触发；putFlux 自动 .cache() 保证共享同一实例
        context.putFlux(NODE_NAME, context.getFlux(inputFluxKey, AgentStreamEvent.class));

        if (terminal) {
            // 链尾（无 AUDIO）：本节点作为唯一订阅触发方
            Flux<AgentStreamEvent> cachedFlux = context.getFlux(inputFluxKey, AgentStreamEvent.class);
            Disposable subscribe = cachedFlux.subscribe();
            conversationStreamHolder.addStream(conversationId, subscribe);
            log.debug("[管道-虚拟形象输出节点] 透传订阅完成，会话ID={}，表情/动作通道已激活", conversationId);
        }
    }

    // ==================== 指令生成与下发 ====================

    /**
     * 下发表情/动作指令：
     * 表情为持续状态（走 {@code /avatar-2d/expression}），动作为一次性触发（走 {@code /avatar-2d/motion}）。
     * 委托 {@link ConversationWebSocketManager} 统一走 STOMP 点对点推送（路径拼接 /{conversationId}）。
     */
    private void publishCommand(AvatarModel.AvatarCommandPayload command) {
        try {
            if (command.expression() != null) {
                conversationWebSocketManager.sendAvatarExpression(userId, conversationId, command.expression());
            }
            if (command.motion() != null) {
                conversationWebSocketManager.sendAvatarMotion(userId, conversationId, command.motion());
            }
        } catch (Exception e) {
            log.warn("[管道-虚拟形象输出节点] 表情/动作下发失败（不影响正文与TTS），会话ID：{}，原因：{}",
                    conversationId, e.getMessage());
        }
    }

    // ==================== Socket 路径与说明 ====================

    @Override
    public List<NodeEndpoint> getSocketInfo() {
        return List.of(
                NodeEndpoint.builder()
                        .description("AI虚拟形象表情指令（客户端需拼接/{conversationId}）")
                        .path(AvatarConstant.AI_AVATAR_EXPRESSION)
                        .build(),
                NodeEndpoint.builder()
                        .description("AI虚拟形象动作指令（客户端需拼接/{conversationId}）")
                        .path(AvatarConstant.AI_AVATAR_MOTION)
                        .build()
        );
    }

}