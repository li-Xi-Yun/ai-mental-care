package org.lixiyun.common.agent.asr.api;

import org.lixiyun.common.agent.asr.model.AsrResult;

/**
 * ASR实时语音识别结果回调接口
 * <p>所有方法均为default空实现，调用方可按需重写感兴趣的回调事件。</p>
 *
 * <h3>回调时序</h3>
 * <pre>
 * onTranscriberStart → onSentenceBegin →
 *   (onIntermediateResult)* →
 * onSentenceEnd → onComplete
 * </pre>
 * <p>{@link #onComplete()} 为ASR会话生命周期回调，在识别结束后调用，可用于清理资源。</p>
 * 若识别失败则回调{@link #onError(String, String)}替代{@link #onSentenceEnd(AsrResult)}和{@link #onComplete()}
 *
 * @author lixiyun
 * @since 2026-09-26
 */
public interface AsrResultCallback {

    /**
     * 中间识别结果回调
     *
     * @param result 标准化AsrResult，包含text/sentenceIndex等字段
     */
    default void onIntermediateResult(AsrResult result) {
    }

    /**
     * 一句话开始回调（服务端智能断句）
     *
     * @param result 标准化AsrResult，包含text/sentenceIndex等字段
     */
    default void onSentenceBegin(AsrResult result) {
    }

    /**
     * 一句话结束回调
     *
     * @param result 标准化AsrResult，包含text/sentenceIndex/startTime/endTime/confidence等字段
     */
    default void onSentenceEnd(AsrResult result) {
    }

    /**
     * 识别开始回调
     *
     * @param taskId 服务端分配的识别任务ID
     */
    default void onTranscriberStart(String taskId) {
    }

    /**
     * ASR识别会话结束回调（连接关闭前最后一次通知）
     * <p>每轮识别结束后调用，无论是否产生识别结果。可用于清理会话级资源。</p>
     */
    default void onComplete() {
    }

    /**
     * 识别失败回调
     *
     * @param taskId     识别任务ID
     * @param statusText 错误状态描述
     */
    default void onError(String taskId, String statusText) {
    }

    // ==================== 已废弃方法（保留以兼容旧实现，待迁移完成后删除） ====================

    /**
     * @deprecated 请使用 {@link #onIntermediateResult(AsrResult)} 替代
     */
    @Deprecated
    default void onIntermediateResult(String text, int sentenceIndex) {
    }

    /**
     * @deprecated 请使用 {@link #onSentenceBegin(AsrResult)} 替代
     */
    @Deprecated
    default void onSentenceBegin(String text, int sentenceIndex) {
    }

    /**
     * @deprecated 请使用 {@link #onSentenceEnd(AsrResult)} 替代
     */
    @Deprecated
    default void onSentenceEnd(String text, int sentenceIndex, long beginTime, long time, double confidence) {
    }
}