package org.lixiyun.server.infrastructure.interaction.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.OutputPipelineExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.infrastructure.interaction.pipeline.node.ModelChatOutputNode;
import org.lixiyun.server.infrastructure.interaction.pipeline.node.TtsOutputNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 输出管道组装工厂
 *
 * <p>职责：
 * <ul>
 *   <li>根据 outputTypes 组装节点链（ModelChatOutputNode、TtsNode 等）</li>
 *   <li>产出可执行的 OutputPipeline 实例</li>
 * </ul>
 *
 * <p>组装规则（每会话独立实例）：
 * <pre>
 * {TEXT}        → [ModelChatOutputNode]
 * {AUDIO}       → [ModelChatOutputNode, TtsNode]
 * {TEXT, AUDIO} → [ModelChatOutputNode, TtsNode]
 * </pre>
 *
 * <h3>为什么使用 ObjectProvider 而非直接注入节点 Bean：</h3>
 * <p>ModelChatOutputNode / TtsNode 声明为 {@code @Scope("prototype")}，且持有会话级状态
 * （conversationId / userId），<b>必须为每个会话创建独立的节点实例</b>。</p>
 * <ul>
 *   <li>若通过 {@code List<OutputNode>} 构造注入，Spring 对 prototype Bean 只会创建一次放入集合，
 *       所有会话复用同一批实例 → 字段会被不同会话的 init() 互相覆盖，销毁/中断时拿错会话。</li>
 *   <li>改用 {@link ObjectProvider}，每次 {@code getObject()} 都会向容器请求一个新实例，
 *       保证"每次组装管道 = 新建节点实例"，prototype 语义真正生效。</li>
 * </ul>
 *
 * <p>扩展方式：新增输出类型只需新增一个 OutputNode 实现类并注册到 Spring 容器，
 * 然后在 {@link #buildNodeChain} 中补充组装规则即可。</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutputPipelineFactory {

    /**
     * ModelChatOutputNode 的对象提供者——每次 getObject() 创建该会话独立的节点实例
     */
    private final ObjectProvider<ModelChatOutputNode> modelChatNodeProvider;

    /**
     * TtsNode 的对象提供者——每次 getObject() 创建该会话独立的节点实例
     */
    private final ObjectProvider<TtsOutputNode> ttsNodeProvider;

    /**
     * 根据输出模式集合组装管道
     *
     * @param outputTypes    前端请求的输出模式集合（如 {TEXT, AUDIO}）
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 组装好的 OutputPipeline 实例
     */
    public OutputPipeline createPipeline(Set<OutputDataType> outputTypes,
                                         Long conversationId, Long userId) {
        List<OutputNode> nodeChain = buildNodeChain(outputTypes);
        log.info("[输出管道工厂] 组装管道完成，outputTypes={}，节点链={}",
                outputTypes, nodeChain.stream().map(OutputNode::getNodeName).toList());
        return new OutputPipeline(nodeChain, conversationId, userId);
    }

    /**
     * 根据 outputTypes 组装节点链（每会话全新实例）
     *
     * <p>组装规则：
     * <pre>
     * {TEXT}        → [ModelChatOutputNode]
     * {AUDIO}       → [ModelChatOutputNode, TtsNode]
     * {TEXT, AUDIO} → [ModelChatOutputNode, TtsNode]
     * </pre>
     * 音频输出需要 ModelChatOutputNode 提供文本流 → TtsNode 消费；纯文本输出只需要 ModelChatOutputNode。
     * 每次调用都通过 ObjectProvider.getObject() 新建节点实例，避免会话间字段串扰。
     *
     * @param outputTypes 前端请求的输出模式集合
     * @return 有序节点链
     */
    protected List<OutputNode> buildNodeChain(Set<OutputDataType> outputTypes) {
        if (outputTypes == null || outputTypes.isEmpty()) {
            log.error("[输出管道工厂] outputTypes 为空");
            throw new BusinessException(OutputPipelineExceptionEnum.OUTPUT_TYPES_EMPTY);
        }

        boolean needAudio = outputTypes.contains(OutputDataType.AUDIO);
        if (needAudio) {
            log.debug("[输出管道工厂] 组装音频输出节点链：ModelChatOutputNode → TtsNode");
            return List.of(modelChatNodeProvider.getObject(), ttsNodeProvider.getObject());
        }

        log.debug("[输出管道工厂] 组装纯文本输出节点链：ModelChatOutputNode");
        return List.of(modelChatNodeProvider.getObject());
    }
}