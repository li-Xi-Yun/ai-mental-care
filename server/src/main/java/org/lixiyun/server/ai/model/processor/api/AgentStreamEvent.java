package org.lixiyun.server.ai.model.processor.api;

import org.lixiyun.server.ai.model.processor.decorator.ThinkAccumulateDecorator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * 流式领域事件 - 统一封装Agent输出事件
 * <p>
 * 使用 sealed interface 约束事件类型，所有合法事件均在此外壳内定义，
 * 外部不可自行扩展，保证事件集封闭性与模式匹配穷举安全。
 * </p>
 *
 * <h3>事件分类：</h3>
 * <ul>
 *     <li><b>流式片段事件</b> - ModelContentChunk / ModelThinkChunk（逐帧推送）</li>
 *     <li><b>终态事件</b> - ModelComplete / ModelToolCall / ToolResponseReceived（一轮结束）</li>
 *     <li><b>聚合事件</b> - FullThinkCompleted（由 ThinkAccumulateDecorator 在流结束时发射）</li>
 *     <li><b>生命周期事件</b> - StreamError / StreamFinished（全局信号）</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
public sealed interface AgentStreamEvent
        permits AgentStreamEvent.ModelContentChunk,
        AgentStreamEvent.ModelThinkChunk,
        AgentStreamEvent.ModelComplete,
        AgentStreamEvent.ModelToolCall,
        AgentStreamEvent.ToolResponseReceived,
        AgentStreamEvent.FullThinkCompleted,
        AgentStreamEvent.StreamError,
        AgentStreamEvent.StreamFinished {

    /**
     * 模型流式正文片段
     *
     * @param text 正文文本片段
     */
    record ModelContentChunk(String text) implements AgentStreamEvent {}

    /**
     * 模型思考推理片段（reasoning_content 逐帧推送）
     *
     * @param reasoning 推理文本片段
     */
    record ModelThinkChunk(String reasoning) implements AgentStreamEvent {}

    /**
     * 模型最终回复完成（无工具调用）
     *
     * @param message 包含完整回复内容的 AssistantMessage
     */
    record ModelComplete(AssistantMessage message) implements AgentStreamEvent {}

    /**
     * 模型发起工具调用请求（hasToolCalls = true）
     *
     * @param message 包含工具调用信息的 AssistantMessage
     */
    record ModelToolCall(AssistantMessage message) implements AgentStreamEvent {}

    /**
     * 工具执行返回结果
     *
     * @param message 工具响应消息
     */
    record ToolResponseReceived(ToolResponseMessage message) implements AgentStreamEvent {}

    /**
     * 思考内容聚合完成事件
     * <p>
     * 由 {@link ThinkAccumulateDecorator} 在流结束时发射，
     * 携带完整的思考文本，供持久化装饰器存储为 ThinkMessage。
     * </p>
     *
     * @param fullText 完整的思考推理文本
     */
    record FullThinkCompleted(String fullText) implements AgentStreamEvent {}

    /**
     * 流式处理全局异常
     *
     * @param throwable 异常对象
     */
    record StreamError(Throwable throwable) implements AgentStreamEvent {}

    /**
     * 流正常结束信号
     */
    record StreamFinished() implements AgentStreamEvent {}
}