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
 *     <li>{@link #onFinished} - 流程结束（无论成功或异常）</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-15
 */
public interface StreamEventListener {

    default void onContentChunk(String text) {}

    default void onThinkChunk(String reasoning) {}

    default void onFullThinkCompleted(String fullText) {}

    default void onModelComplete(AssistantMessage message) {}

    default void onToolCall(AssistantMessage message) {}

    default void onToolResponse(ToolResponseMessage message) {}

    default void onError(Throwable err) {}

    default void onFinished() {}
}