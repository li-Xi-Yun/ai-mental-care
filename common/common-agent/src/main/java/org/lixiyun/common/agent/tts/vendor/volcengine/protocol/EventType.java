package org.lixiyun.common.agent.tts.vendor.volcengine.protocol;

import lombok.Getter;

@Getter
public enum EventType {
    /** 无事件 */
    NONE(0),

    /** 开始连接 */
    START_CONNECTION(1),
    /** 开始任务 */
    START_TASK(1),
    /** 结束连接 */
    FINISH_CONNECTION(2),
    /** 结束任务 */
    FINISH_TASK(2),

    /** 连接已建立 */
    CONNECTION_STARTED(50),
    /** 任务已启动 */
    TASK_STARTED(50),
    /** 连接失败 */
    CONNECTION_FAILED(51),
    /** 任务失败 */
    TASK_FAILED(51),
    /** 连接已结束 */
    CONNECTION_FINISHED(52),
    /** 任务已结束 */
    TASK_FINISHED(52),

    /** 开始会话 */
    START_SESSION(100),
    /** 取消会话 */
    CANCEL_SESSION(101),
    /** 结束会话 */
    FINISH_SESSION(102),

    /** 会话已启动 */
    SESSION_STARTED(150),
    /** 会话已取消 */
    SESSION_CANCELED(151),
    /** 会话已结束 */
    SESSION_FINISHED(152),
    /** 会话失败 */
    SESSION_FAILED(153),
    /** 用量响应 */
    USAGE_RESPONSE(154),
    /** 计费数据 */
    CHARGE_DATA(154),

    /** 任务请求 */
    TASK_REQUEST(200),
    /** 更新配置 */
    UPDATE_CONFIG(201),

    /** 音频已静音 */
    AUDIO_MUTED(250),

    /** 心跳消息 */
    SAY_HELLO(300),

    /** TTS句子开始 */
    TTS_SENTENCE_START(350),
    /** TTS句子结束 */
    TTS_SENTENCE_END(351),
    /** TTS响应 */
    TTS_RESPONSE(352),
    /** TTS结束 */
    TTS_ENDED(359),
    /** 播客片段开始 */
    PODCAST_ROUND_START(360),
    /** 播客片段响应 */
    PODCAST_ROUND_RESPONSE(361),
    /** 播客片段结束 */
    PODCAST_ROUND_END(362),

    /** ASR信息 */
    ASR_INFO(450),
    /** ASR响应 */
    ASR_RESPONSE(451),
    /** ASR结束 */
    ASR_ENDED(459),

    /** 对话TTS文本 */
    CHAT_TTS_TEXT(500),

    /** 对话响应 */
    CHAT_RESPONSE(550),
    /** 对话结束 */
    CHAT_ENDED(559),

    /** 源语言字幕开始 */
    SOURCE_SUBTITLE_START(650),
    /** 源语言字幕响应 */
    SOURCE_SUBTITLE_RESPONSE(651),
    /** 源语言字幕结束 */
    SOURCE_SUBTITLE_END(652),
    /** 翻译字幕开始 */
    TRANSLATION_SUBTITLE_START(653),
    /** 翻译字幕响应 */
    TRANSLATION_SUBTITLE_RESPONSE(654),
    /** 翻译字幕结束 */
    TRANSLATION_SUBTITLE_END(655);

    private final int value;

    EventType(int value) {
        this.value = value;
    }

    public static EventType fromValue(int value) {
        for (EventType type : EventType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown EventType value: " + value);
    }
}