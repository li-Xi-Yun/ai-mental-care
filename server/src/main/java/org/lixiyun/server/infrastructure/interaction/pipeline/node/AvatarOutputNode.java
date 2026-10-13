package org.lixiyun.server.infrastructure.interaction.pipeline.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.entity.config.Live2dConfig;
import org.lixiyun.server.ai.model.processor.api.AgentStreamEvent;
import org.lixiyun.server.infrastructure.conversation.ConversationStreamHolder;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputContext;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputDataType;
import org.lixiyun.server.infrastructure.interaction.pipeline.OutputNode;
import org.lixiyun.server.mapper.Live2dConfigMapper;
import org.lixiyun.server.socket.constant.AvatarConstant;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 虚拟形象（Live2D）输出节点
 *
 * <p><b>职责</b>：实现「控制段剥离 + 正文重包装」的前缀剥离节点（方案B），处于
 * {@code TEXT → AVATAR_2D → TTS} 节点链的中间位置：</p>
 * <ul>
 *   <li>从 {@link OutputContext} 获取上游 {@code ModelChatOutputNode} 产出的 cached
 *       {@code Flux<AgentStreamEvent>}（key 为 {@link ModelChatOutputNode#NODE_NAME}）</li>
 *   <li>通过节点内部的有状态剥离器 {@link AvatarControlStrip} 检测首个连续终止符 {@code +++}：
 *       其前的控制段 JSON **解析一次**（提取 expression / motion）→ 白名单校验 → 经 WebSocket
 *       分别下发 {@code /avatar/expression} / {@code /avatar/motion}；其后的内容作为正文透传</li>
 *   <li>剥离控制段后的正文事件重包装为该节点产出 Flux（key 为 {@link AvatarOutputNode#NODE_NAME}）写入
 *       {@link OutputContext}：含 `AUDIO` 输出时由 {@code TtsOutputNode} 消费（TTS 不会朗读控制段），
 *       不含 `AUDIO` 时本节点作为链尾自行订阅触发</li>
 * </ul>
 *
 * <p><b>关键设计（方案B）</b>：
 * <ul>
 *   <li><b>零额外 LLM 调用</b>：消费的是既有 cached Flux，不触发上游；逗号/短句等碎片仅进缓冲，不逐帧解析</li>
 *   <li><b>副作用只执行一次</b>：控制段下发放置在 transform 内，正文 Flux 经 putFlux 再 .cache()，
 *       下游订阅属重放，不会重复发布指令</li>
 *   <li><b>剥离逻辑内聚</b>：剥离器作为本节点的内部实现，不单独建类（全链只有本节点负责剥离）</li>
 *   <li><b>降级安全</b>：剥离/校验/推送任何异常仅影响表情/动作，不影响正文与 TTS 通道</li>
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
public class AvatarOutputNode implements OutputNode {

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
        this.conversationId = conversationId;
        this.userId = userId;
        log.info("[管道-虚拟形象输出节点] 节点初始化完成，会话ID={}，用户ID={}", conversationId, userId);
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
     * 前缀剥离处理：从 OutputContext 取上游 cached Flux，经内部剥离器 transform，
     * 将控制段解析下发、剩余正文重包装为正文 Flux（key 为 {@link AvatarOutputNode#NODE_NAME}）写入 OutputContext，并订阅触发副作用。
     *
     * @param context 输出上下文
     */
    @Override
    public void process(OutputContext context) {
        boolean avatarOutput = context.hasOutputType(OutputDataType.AVATAR_2D);
        if (!avatarOutput) {
            log.debug("[管道-虚拟形象输出节点] 非虚拟形象输出模式，跳过处理，会话ID：{}", conversationId);
            return;
        }

        Flux<AgentStreamEvent> aiResponse = context.getFlux(inputFluxKey, AgentStreamEvent.class);

        // 白名单来自启用的 Live2D 模型配置（懒加载；无配置/异常时为空集，严格放行不命中）
        DataConfig config = loadAllowList();

        AvatarControlStrip stripper = new AvatarControlStrip(config.expressionList(), config.motionList());
        Flux<AgentStreamEvent> bodyFlux = aiResponse.transform(flux -> flux.handle((event, sink) -> {
            if (event instanceof AgentStreamEvent.ModelContentChunk(String text)) {
                StripResult result = stripper.accept(text);
                if (result.command() != null) {
                    publishCommand(result.command());
                }
                if (result.bodyText() != null && !result.bodyText().isEmpty()) {
                    sink.next(new AgentStreamEvent.ModelContentChunk(result.bodyText()));
                }
            } else {
                // ModelComplete / StreamFinished / StreamError 等事件原样透传
                sink.next(event);
            }
        }));

        if (!terminal) {
            // 非链尾：剩余正文重包装写入 OutputContext（内部 .cache()，供 TTS 消费）
            context.putFlux(NODE_NAME, bodyFlux);
            return;
        }

        // 链尾（无 AUDIO）：本节点作为唯一订阅触发方
        Disposable subscribe = bodyFlux.subscribe();
        conversationStreamHolder.addStream(conversationId, subscribe);

        log.debug("[管道-虚拟形象输出节点] 控制段剥离订阅完成，会话ID={}，表情/动作通道已激活", conversationId);
    }

    // ==================== 控制段剥离与下发 ====================

    /**
     * 下发表情/动作指令：
     * 表情为持续状态（走 {@code /avatar-2d/expression}），动作为一次性触发（走 {@code /avatar-2d/motion}）。
     * 委托 {@link ConversationWebSocketManager} 统一走 STOMP 点对点推送（路径拼接 /{conversationId}）。
     */
    private void publishCommand(AvatarCommandPayload command) {
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

    /**
     * 加载白名单：查询当前启用的 Live2D 模型配置，提取 expressionList / motionList。
     * 查询异常或未配置模型时返回空集（严格放行，不误发表情/动作）。
     */
    private DataConfig loadAllowList() {
        try {
            Live2dConfig config = live2dConfigMapper.selectOne(new LambdaQueryWrapper<Live2dConfig>()
                    .eq(Live2dConfig::getEnabled, Live2dConfig.ENABLED_YES)
                    .orderByAsc(Live2dConfig::getSort)
                    .last("limit 1"));
            if (config == null || (config.getExpressionList() == null && config.getMotionList() == null)) {
                return new DataConfig(Set.of(), Set.of());
            }
            List<String> expressionList = config.getExpressionList();
            List<String> motionList = config.getMotionList();

            return new DataConfig(
                    expressionList != null ? expressionList.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.toSet()) : Set.of(),
                    motionList != null ? motionList.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.toSet()) : Set.of());
        } catch (Exception e) {
            log.warn("[管道-虚拟形象输出节点] 白名单加载失败，本次按空集处理，会话ID：{}，原因：{}", conversationId, e.getMessage());
            return new DataConfig(Set.of(), Set.of());
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

    // ==================== 内部数据结构 ====================

    private record DataConfig(Set<String> expressionList, Set<String> motionList) {
    }

    /**
     * 控制段剥离结果：command 非空表示控制段结束需下发；bodyText 为剥离控制段后的正文片段。
     */
    private record StripResult(AvatarCommandPayload command, String bodyText) {
    }

    /**
     * 表情/动作指令载荷：字段均为资产 id，任一为 null 表示该通道不下发。
     */
    private record AvatarCommandPayload(String expression, String motion) {
    }

    /**
     * 有状态控制段剥离器（节点内部实现，不单独建类）。
     *
     * <p>行为：
     * <ul>
     *   <li>正文开始前所有文本追加到控制段缓冲，检测到首个 {@code +++} 后解析一次并返回 command；</li>
     *   <li>控制段确定结束后，后续所有文本作为正文透传；</li>
     *   <li>缓冲超长仍未出现 {@code +++} → 放弃控制段，全部按正文放行；</li>
     *   <li>解析失败 / 白名单未命中 → 不报错、不阻塞，仅丢弃该指令。</li>
     * </ul>
     * </p>
     */
    private final class AvatarControlStrip {

        /** 控制段缓冲区 */
        private final StringBuilder controlBuffer = new StringBuilder();
        /** 表情白名单 */
        private final Set<String> expressionAllow;
        /** 动作白名单 */
        private final Set<String> motionAllow;
        /** 是否已完成控制段/正文的边界判定（一旦完成，后续 chunk 全为正文） */
        private boolean headerComplete = false;

        private AvatarControlStrip(Set<String> expressionAllow, Set<String> motionAllow) {
            this.expressionAllow = expressionAllow;
            this.motionAllow = motionAllow;
        }

        StripResult accept(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return new StripResult(null, null);
            }
            if (headerComplete) {
                return new StripResult(null, chunk);
            }

            controlBuffer.append(chunk);
            String buf = controlBuffer.toString();
            int termIdx = buf.indexOf(CONTROL_TERMINATOR);
            if (termIdx >= 0) {
                headerComplete = true;
                controlBuffer.setLength(0);
                String controlPart = buf.substring(0, termIdx).trim();
                String body = buf.substring(termIdx + CONTROL_TERMINATOR.length());
                AvatarCommandPayload command = parseControl(controlPart);
                return new StripResult(command, body.isEmpty() ? null : body);
            }
            if (controlBuffer.length() > MAX_CONTROL_LENGTH) {
                // 超长未见终止符：放弃控制段，全部按正文放行
                headerComplete = true;
                String all = controlBuffer.toString();
                controlBuffer.setLength(0);
                return new StripResult(null, all);
            }
            // 控制段尚未结束，继续累积（正文碎片仅进缓冲，不触发解析）
            return new StripResult(null, null);
        }

        /**
         * 解析控制段，返回表情/动作指令载荷。
         * @param controlPart 控制段文本
         * @return 表情/动作指令载荷
         */
        private AvatarCommandPayload parseControl(String controlPart) {
            if (controlPart.isEmpty()) {
                return null;
            }
            try {
                AvatarCommandPayload payload = JsonUtils.parseObject(controlPart,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
                if (payload == null) {
                    return null;
                }
                String expression = payload.expression() != null && expressionAllow.contains(payload.expression())
                        ? payload.expression()
                        : null;
                String motion = payload.motion() != null && motionAllow.contains(payload.motion())
                        ? payload.motion()
                        : null;
                if (expression == null && motion == null) {
                    log.debug("[管道-虚拟形象输出节点] 控制段空白名单未命中，不下发，会话ID：{}，控制段：{}", conversationId, controlPart);
                    return null;
                }
                return new AvatarCommandPayload(expression, motion);
            } catch (Exception e) {
                log.warn("[管道-虚拟形象输出节点] 控制段 JSON 解析失败（降级为默认表情，不阻塞正文），会话ID：{}，原因：{}",
                        conversationId, e.getMessage());
                return null;
            }
        }
    }
}