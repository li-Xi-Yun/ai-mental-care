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