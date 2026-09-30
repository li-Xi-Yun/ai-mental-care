package org.lixiyun.server.ai.model.processor.api;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * 流式事件监听器接口
 * <p>
 * 替代零散的 {@code Consumer<String>} / {@code Consumer<AssistantMessage>} 回调参数，
 * 提供统一的语义抽象，所有事件回调均通过此接口分发。
 * </p>
 *
 * <h3>回调语义说明：</h3>
 * <ul>
 *     <li>{@link #onContentChunk} - 模型正文逐帧推送，用于 WebSocket 流式转发</li>
 *     <li>{@link #onThinkChunk} - 模型思考推理逐帧推送，用于前端实时展示思考过程</li>
 *     <li>{@link #onFullThinkCompleted} - 思考内容聚合完成，携带完整推理文本</li>
 *     <li>{@link #onModelComplete} - 模型最终回复完成（仅无工具调用时触发）</li>
 *     <li>{@link #onToolCall} - 模型发起工具调用请求</li>
 *     <li>{@link #onToolResponse} - 工具执行返回结果</li>
 *     <li>{@link #onError} - 流式处理异常</li>
 *     <li>{@link #onInterrupted} - 流被中断（外部主动 dispose 订阅），用于保存已输出部分内容等后置处理</li>
 *     <li>{@link #onFinished} - 流程结束（无论成功、异常或中断）</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
public interface StreamEventListener {

    /**
     * 模型正文内容逐帧推送回调
     * <p>
     * 模型生成文本时，每生成一个 token 或分片即触发一次，适用于 WebSocket 流式转发到前端。
     * </p>
     *
     * @param text 当前帧的正文文本片段
     */
    default void onContentChunk(String text) {}

    /**
     * 模型思考/推理过程逐帧推送回调
     * <p>
     * 用于支持深度思考模型的推理过程展示，每生成一个推理 token 即触发一次，
     * 前端可实时渲染模型"思考中"的中间输出。
     * </p>
     *
     * @param reasoning 当前帧的思考/推理文本片段
     */
    default void onThinkChunk(String reasoning) {}

    /**
     * 完整思考内容聚合完成回调
     * <p>
     * 当模型思考阶段结束后触发，携带完整的推理文本。
     * 与 {@link #onThinkChunk} 配合使用：前者用于逐帧实时展示，本回调用于最终存储或汇总。
     * </p>
     *
     * @param fullText 完整的思考/推理文本
     */
    default void onFullThinkCompleted(String fullText) {}

    /**
     * 模型最终回复完成回调（无工具调用场景）
     * <p>
     * 仅当模型直接完成回复、未触发工具调用时触发。如果存在工具调用，则通过
     * {@link #onToolCall} 和 {@link #onToolResponse} 处理交互流程。
     * </p>
     *
     * @param message 模型生成的完整回复消息
     */
    default void onModelComplete(AssistantMessage message) {}

    /**
     * 模型发起工具调用请求回调
     * <p>
     * 模型在对话过程中决定调用外部工具（如 Function Calling）时触发，
     * 携带工具调用的名称、参数等元数据。
     * </p>
     *
     * @param message 包含工具调用请求的 Assistant 消息
     */
    default void onToolCall(AssistantMessage message) {}

    /**
     * 工具执行返回结果回调
     * <p>
     * 当被调用的工具执行完毕并返回结果时触发，携带工具的执行输出。
     * 该结果通常会被反馈给模型以继续生成回复。
     * </p>
     *
     * @param message 工具执行返回的响应消息
     */
    default void onToolResponse(ToolResponseMessage message) {}

    /**
     * 流式处理异常回调
     * <p>
     * 当流式对话过程中发生任何异常时触发，包括但不限于网络错误、
     * 模型服务异常、序列化错误等。
     * </p>
     *
     * @param err 发生的异常对象
     */
    default void onError(Throwable err) {}

    /**
     * 流被中断回调
     * <p>
     * 当外部主动 dispose 订阅导致流被中断时触发，用于执行保存已输出内容、
     * 清理资源等后置处理逻辑。
     * </p>
     */
    default void onInterrupted() {}

    /**
     * 流程结束回调
     * <p>
     * 无论流式处理是正常完成、发生异常还是被中断，最终都会触发此回调，
     * 用于统一的后置清理和状态收尾工作。
     * </p>
     */
    default void onFinished() {}
}