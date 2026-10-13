package org.lixiyun.server.infrastructure.interaction.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.OutputPipelineExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.infrastructure.interaction.pipeline.node.AvatarOutputNode;
import org.lixiyun.server.infrastructure.interaction.pipeline.node.ModelChatOutputNode;
import org.lixiyun.server.infrastructure.interaction.pipeline.node.TtsOutputNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 输出管道组装工厂
 *
 * <p>职责：
 * <ul>
 *   <li>根据 outputTypes 通过声明式节点注册表 {@link NodeDef} 组装节点链（ModelChatOutputNode、AvatarOutputNode、TtsNode 等）</li>
 *   <li>产出可执行的 OutputPipeline 实例</li>
 * </ul>
 *
 * <p>组装规则（声明式，零组合硬编码）：按 {@link NodeDef#servesType()} 过滤（源节点恒在，
 * 其余节点取 outputTypes 命中的），按 {@link NodeDef#order()} 升序排序，前向链接注入
 * [上一节点产出 key → setInputFluxKey]，并标记链尾 setTerminal(true)（唯一订阅触发方）。</p>
 *
 * <h3>为什么使用 ObjectProvider 而非直接注入节点 Bean：</h3>
 * <p>ModelChatOutputNode / TtsNode / AvatarOutputNode 声明为 {@code @Scope("prototype")}，且持有会话级状态
 * （conversationId / userId），<b>必须为每个会话创建独立的节点实例</b>。</p>
 * <ul>
 *   <li>若通过 {@code List<OutputNode>} 构造注入，Spring 对 prototype Bean 只会创建一次放入集合，
 *       所有会话复用同一批实例 → 字段会被不同会话的 init() 互相覆盖，销毁/中断时拿错会话。</li>
 *   <li>改用 {@link ObjectProvider}，每次 {@code getObject()} 都会向容器请求一个新实例，
 *       保证"每次组装管道 = 新建节点实例"，prototype 语义真正生效。</li>
 * </ul>
 *
 * <p>扩展方式：新增输出类型只需新增一个 OutputNode 实现类（声明 {@code servesType()/order()}）、
 * 注册到 Spring 容器，并在 {@link #buildNodeDefs()} 中增加一条 {@link NodeDef} 即可——装配循环零改动。</p>
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
     * AvatarOutputNode 的对象提供者——每次 getObject() 创建该会话独立的节点实例
     */
    private final ObjectProvider<AvatarOutputNode> avatarOutputNodeProvider;

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
     * 声明式节点注册表——新增输出类型只需在此增加一条 {@link NodeDef}，装配循环零改动。
     *
     * <p>源节点（{@code servesType()==null}）恒在链中；其余节点由
     * {@code outputTypes.contains(servesType())} 决定是否入链。{@code order()} 决定消费顺序。</p>
     *
     * @return 节点注册表
     */
    protected List<NodeDef> buildNodeDefs() {
        return List.of(
                // 模型输出：源节点，任何输出组合都必须存在
                new NodeDef("modelChatOutputNode", null, 0, modelChatNodeProvider::getObject),
                // 虚拟形象：可选输出（AVATAR_2D），在 TTS 之前剥离控制段并产出正文 Flux
                new NodeDef("avatarOutputNode", OutputDataType.AVATAR_2D, 10, avatarOutputNodeProvider::getObject),
                // TTS：可选输出（AUDIO），链尾（消费链末产出，其自身再产出为空则整链由它触发）
                new NodeDef("ttsOutputNode", OutputDataType.AUDIO, 20, ttsNodeProvider::getObject)
        );
    }

    /**
     * 声明式通用装配：filter（源节点恒在 + outputTypes 命中）→ sort（order 升序）→ 前向链接注入 → 标记链尾。
     *
     * <p>任意 outputTypes 组合（含未来新类型）都由该循环自动得出，无需手工枚举组合分支。</p>
     *
     * @param outputTypes 前端请求的输出模式集合
     * @return 有序节点链
     */
    protected List<OutputNode> buildNodeChain(Set<OutputDataType> outputTypes) {
        if (outputTypes == null || outputTypes.isEmpty()) {
            log.error("[输出管道工厂] outputTypes 为空");
            throw new BusinessException(OutputPipelineExceptionEnum.OUTPUT_TYPES_EMPTY);
        }

        List<NodeDef> defs = buildNodeDefs().stream()
                .filter(def -> def.servesType() == null || outputTypes.contains(def.servesType()))
                .sorted(Comparator.comparingInt(NodeDef::order))
                .toList();

        List<OutputNode> chain = new ArrayList<>(defs.size());
        for (int i = 0; i < defs.size(); i++) {
            NodeDef def = defs.get(i);
            OutputNode node = def.supplier().get();               // prototype，每会话新实例
            if (i == 0) {
                node.setInputFluxKey(null);                        // 源节点无上游
            } else {
                node.setInputFluxKey(chain.get(i - 1).producedFluxKey());  // 注入上一节点产出 key
            }
            node.setTerminal(i == defs.size() - 1);               // 链尾唯一（订阅触发方）
            chain.add(node);
        }
        log.debug("[输出管道工厂] 声明式组装节点链完成：{}", chain.stream().map(OutputNode::getNodeName).toList());
        return chain;
    }
}