package org.lixiyun.server.infrastructure.interaction.pipeline;

import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;

import java.util.List;

/**
 * 输出节点接口——管道中的一个"过滤器"
 *
 * <p>节点接收 OutputContext（共享状态池），从中读取上游节点产出的数据，
 * 处理后写入产出的 Flux 或数据到 context 中，供下游节点消费。
 * 节点之间不直接传递数据，而是通过 OutputContext 的 fluxStore / dataStore 解耦。</p>
 *
 * <h3>设计要点：</h3>
 * <ul>
 *   <li>副作用内聚于节点：WebSocket 推送、数据库存储等副作用由各节点在 process() 内部自行处理</li>
 *   <li>输出方式由 outputTypes 驱动：节点根据 context.hasOutputType() 判断是否附加推送副作用</li>
 *   <li>接口极简化：process(OutputContext) 返回 void，节点间通过 context.fluxStore/dataStore 隐式通信</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
public interface OutputNode {

    /**
     * 节点名称
     *
     * @return 节点标识名（如 "ModelNode", "TtsNode"）
     */
    String getNodeName();

    // ==================== 声明式装配契约（由 OutputPipelineFactory 统一读取） ====================

    /**
     * 本节点负责的输出类型；{@code null} 表示无论 outputTypes 如何该节点都必须存在（源节点，如模型输出）。
     *
     * <p>Factory 依据该类型判断节点是否进入节点链（{@code outputTypes.contains(servesType()) 或 servesType()==null}），
     * 实现泛化装配，无需逐组合硬编码。</p>
     *
     * @return 服务类型；可为 {@code null}
     */
    default OutputDataType servesType() {
        return null;
    }

    /**
     * 链内排序（消费顺序），数值越小越靠前。
     *
     * @return 节点在链中的次序
     */
    default int order() {
        return 0;
    }

    /**
     * 本节点产出 Flux 写入 OutputContext 的 key。
     * 默认取 {@link #getNodeName()}；如产出写入其他 key 可覆写。
     *
     * @return 产出 Flux key
     */
    default String producedFluxKey() {
        return getNodeName();
    }

    /**
     * 由 Factory 装配时注入：上游节点产出的 Flux key。
     * 源节点（无上游）不调用本方法；读取上游的节点应保持该字段并在 {@link #process(OutputContext)} 中使用。
     *
     * @param inputFluxKey 上游产出 key
     */
    default void setInputFluxKey(String inputFluxKey) {
        // 默认空实现；需要读取上游的节点自行覆写并通过字段保持
    }

    /**
     * 由 Factory 装配时注入：是否为节点链的最后一个节点（链尾）。
     * 链尾是唯一订阅触发的节点，其余节点只负责 putFlux 传递。
     *
     * @param terminal 是否链尾
     */
    default void setTerminal(boolean terminal) {
        // 默认空实现；源/链尾订阅相关节点自行覆写
    }

    /**
     * 初始化节点：创建连接、注册回调、分配资源
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     */
    void init(Long conversationId, Long userId);

    /**
     * 销毁节点：释放所有资源、关闭连接
     */
    void destroy();

    /**
     * 中断当前处理：保留实例和连接，中止正在进行的流
     */
    void interrupt();

    /**
     * 核心处理入口
     *
     * <p>节点从 OutputContext 的 dataStore 读取非流式数据（如历史消息、诊断结果），
     * 从 fluxStore 读取上游产出的 Flux 流，处理后将自己的产出写入 context 的 fluxStore/dataStore。</p>
     *
     * @param context 输出上下文（包含所有可用数据，节点按需提取）
     */
    void process(OutputContext context);

    /**
     * 获取该节点中的 Socket 路径和对应的说明信息
     *
     * <p>客户端根据返回的端点列表，连接对应的 WebSocket 路径收发数据。
     * 例如 ModelNode 返回文本流端点，TtsNode 返回音频流端点。</p>
     *
     * @return 该节点的端点信息列表，一定不为null，至少是一个空集合
     */
    List<NodeEndpoint> getSocketInfo();
}