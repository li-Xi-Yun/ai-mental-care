package org.lixiyun.server.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;

/**
 * 节点执行摘要接口
 * <p>为AOP切面提供节点输入/输出摘要提取能力，用于记录节点执行日志，不侵入业务逻辑。</p>
 * <p>实现此接口的节点，在AOP切面拦截时将自动调用 {@link #inputSummary} 和 {@link #outputSummary}
 * 获取结构化摘要数据，序列化为JSON后写入 ai_node_execution 表的 input_summary / output_summary 字段。</p>
 *
 * @author lixiyun
 * @since 2026-09-10
 */
public interface NodeExecutionSummary {

    /**
     * 提取节点输入摘要
     * <p>从全局状态中提取本节点读取的关键输入数据，用于日志记录与后续测评。</p>
     *
     * @param state 工作流全局状态
     * @return 输入摘要对象（将被JSON序列化），若无需记录可返回 null
     */
    Object inputSummary(OverAllState state);

    /**
     * 提取节点输出摘要
     * <p>从全局状态中提取本节点执行后写入的关键输出数据，用于日志记录与后续测评。</p>
     *
     * @param state 工作流全局状态
     * @return 输出摘要对象（将被JSON序列化），若无需记录可返回 null
     */
    Object outputSummary(OverAllState state);
}