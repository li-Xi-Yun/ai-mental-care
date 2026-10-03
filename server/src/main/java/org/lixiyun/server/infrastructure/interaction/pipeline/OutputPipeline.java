package org.lixiyun.server.infrastructure.interaction.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 输出层管道——管理多个 OutputNode 的有序组合
 *
 * <p>每个节点通过 OutputContext 的 fluxStore / dataStore 共享状态池进行隐式通信。
 * Pipeline 按序调用各节点的 process()，节点从 context 读取上游产出、将自身产出写入 context。
 * 副作用（WebSocket 推送、数据库存储等）由各节点在 process() 内部自行处理。</p>
 *
 * <h3>执行流程：</h3>
 * <pre>
 *   1. 按节点列表顺序依次调用 node.process(context)
 *   2. 每个节点从 OutputContext 中读取上游产出的数据
 *   3. 每个节点将自身产出的 Flux/数据写入 OutputContext
 *   4. 下游节点通过 OutputContext 获取上游产出，形成隐式数据流
 * </pre>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
public class OutputPipeline {

    /** 有序的处理节点列表 */
    private final List<OutputNode> nodes;

    /** 所属会话ID */
    private final Long conversationId;

    /** 所属用户ID */
    private final Long userId;

    public OutputPipeline(List<OutputNode> nodes, Long conversationId, Long userId) {
        this.nodes = new ArrayList<>(nodes);
        this.conversationId = conversationId;
        this.userId = userId;
    }

    // ==================== 生命周期 ====================

    /**
     * 初始化管道：按序初始化所有节点
     */
    public void init() {
        for (OutputNode node : nodes) {
            node.init(conversationId, userId);
        }
    }

    /**
     * 销毁管道：逆序销毁所有节点（先关下游再关上游）
     */
    public void destroy() {
        List<OutputNode> reversed = new ArrayList<>(nodes);
        Collections.reverse(reversed);
        for (OutputNode node : reversed) {
            try {
                node.destroy();
            } catch (Exception e) {
                log.error("[管道] 节点销毁失败: {}", node.getNodeName(), e);
            }
        }
    }

    /**
     * 中断管道：中断所有节点的当前操作（保留实例和连接）
     */
    public void interrupt() {
        for (OutputNode node : nodes) {
            try {
                node.interrupt();
            } catch (Exception e) {
                log.error("[管道] 节点中断失败: {}", node.getNodeName(), e);
            }
        }
    }

    // ==================== 核心执行 ====================

    /**
     * 执行输出管道
     *
     * <p>按序调用每个节点的 process(context)，节点内部：
     * <ul>
     *   <li>从 context.dataStore 中读取非流式数据（历史消息、诊断结果等）</li>
     *   <li>从 context.fluxStore 中读取上游节点产出的 Flux 流</li>
     *   <li>将自己的产出写入 context.fluxStore / context.dataStore</li>
     *   <li>根据 context.hasOutputType() 决定是否附加推送副作用</li>
     * </ul>
     *
     * @param context 时间轮触发时编排器装配的上下文数据包
     */
    public void execute(OutputContext context) {
        if (nodes.isEmpty()) {
            return;
        }

        for (OutputNode node : nodes) {
            log.debug("[管道] 节点开始执行: {}", node.getNodeName());
            node.process(context);
        }
    }

    // ==================== Socket 路径与说明 ====================

    /**
     * 获取管道中所有节点的 Socket 路径和说明信息
     *
     * <p>客户端根据返回的路径，直接连接对应的 WebSocket 端点收发数据</p>
     *
     * @return 所有节点的端点信息汇总列表，一定不为null，至少是一个空集合
     */
    public List<NodeEndpoint> getSocketInfo() {
        List<NodeEndpoint> list = new ArrayList<>();
        for (OutputNode node : nodes) {
            list.addAll(node.getSocketInfo());
        }
        return list;
    }
}