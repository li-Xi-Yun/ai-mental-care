package org.lixiyun.common.agent.tts.api;

/**
 * TTS流式合成结果回调接口
 * <p>所有方法均为default空实现，调用方可按需重写感兴趣的回调事件。</p>
 *
 * <h3>回调时序</h3>
 * <pre>
 * onSynthesisStart → onSentenceBegin →
 *   (onAudioData / onSentenceSynthesis)* →
 * onSentenceEnd → ... → onSynthesisComplete
 * </pre>
 * 若合成失败则回调{@link #onFail(String, String)}替代{@link #onSynthesisComplete()}
 *
 * @author lixiyun
 * @since 2026-09-25
 */
public interface TtsResultCallback {

    /**
     * 语音合成开始回调
     */
    default void onSynthesisStart() {
    }

    /**
     * 句子开始回调
     */
    default void onSentenceBegin() {
    }

    /**
     * 句子结束回调
     */
    default void onSentenceEnd() {
    }

    /**
     * 音频数据回调，携带一段PCM/WAV音频字节
     *
     * @param audioData 音频数据字节数组
     */
    default void onAudioData(byte[] audioData) {
    }

    /**
     * 增量时间戳回调
     */
    default void onSentenceSynthesis() {
    }

    /**
     * TTS合成完成回调
     */
    default void onSynthesisComplete() {
    }

    /**
     * TTS合成失败回调
     *
     * @param taskId     失败任务ID
     * @param statusText 失败状态描述
     */
    default void onFail(String taskId, String statusText) {
    }
}