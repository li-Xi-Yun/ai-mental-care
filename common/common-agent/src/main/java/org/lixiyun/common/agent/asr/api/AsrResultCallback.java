package org.lixiyun.common.agent.asr.api;

/**
 * ASR实时语音识别结果回调接口
 * <p>所有方法均为default空实现，调用方可按需重写感兴趣的回调事件。</p>
 *
 * <h3>回调时序</h3>
 * <pre>
 * onTranscriberStart → onSentenceBegin →
 *   (onIntermediateResult)* →
 * onSentenceEnd → ... → onComplete
 * </pre>
 * 若识别失败则回调{@link #onError(String, String)}替代{@link #onComplete()}
 *
 * @author lixiyun
 * @since 2026-09-26
 */
public interface AsrResultCallback {

    /**
     * 中间识别结果回调
     *
     * @param text          当前中间识别文本
     * @param sentenceIndex 句子编号，从1开始递增
     */
    default void onIntermediateResult(String text, int sentenceIndex) {
    }

    /**
     * 识别开始回调
     *
     * @param taskId 服务端分配的识别任务ID
     */
    default void onTranscriberStart(String taskId) {
    }

    /**
     * 一句话开始回调（服务端智能断句）
     *
     * @param text          该句初始识别文本
     * @param sentenceIndex 句子编号，从1开始递增
     */
    default void onSentenceBegin(String text, int sentenceIndex) {
    }

    /**
     * 一句话结束回调
     *
     * @param text          该句最终识别文本
     * @param sentenceIndex 句子编号，从1开始递增
     * @param beginTime     该句在音频流中的开始时间（毫秒）
     * @param time          当前已处理的音频时长（毫秒）
     * @param confidence    该句识别置信度，0.0~1.0
     */
    default void onSentenceEnd(String text, int sentenceIndex, long beginTime, long time, double confidence) {
    }

    /**
     * 整轮识别完毕回调
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
}