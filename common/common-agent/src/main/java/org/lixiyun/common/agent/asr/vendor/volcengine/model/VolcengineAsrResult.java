package org.lixiyun.common.agent.asr.vendor.volcengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 火山引擎ASR二进制响应载荷实体类，精确匹配WebSocket二进制帧中GZIP压缩的JSON载荷结构
 *
 * <p><b>注意：</b>二进制帧头中的{@code code}、{@code is_last_package}、{@code payload_sequence}、
 * {@code payload_size}等字段由{@code AsrResponseParser}从二进制协议头解析后手动设置，
 * 不出现在JSON载荷中，因此不使用{@code @JsonProperty}映射。</p>
 *
 * <pre>
 * 二进制帧载荷JSON结构（即payload_msg内部结构）：
 * {
 *   "audio_info": { "duration": 1842 },
 *   "result": {
 *     "additions": { "log_id": "202609281942138400E35E7B8793BBF6FF" },
 *     "text": "你好。你。",
 *     "utterances": [{
 *       "additions": {
 *         "age": "24.9798240661621094",
 *         "emotion": "neutral",
 *         "emotion_degree": "weak",
 *         "emotion_degree_score": "0.9994799494743347",
 *         "emotion_score": "0.9974225759506226",
 *         "fixed_prefix_result": "",
 *         "gender": "male",
 *         "gender_score": "0.9999083280563354",
 *         "invoke_type": "hard_vad",
 *         "source": "two_pass",
 *         "speaker_id": "0",
 *         "speech_rate": "1.6375545851528384",
 *         "use_bigasr_post_process": "true",
 *         "volume": "75.9444343504191011"
 *       },
 *       "definite": true,
 *       "end_time": 1832,
 *       "text": "你好。你。",
 *       "words": [
 *         { "end_time": 120, "start_time": 40, "text": "你" },
 *         { "end_time": 520, "start_time": 120, "text": "好" }
 *       ]
 *     }]
 *   }
 * }
 * </pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VolcengineAsrResult {

    public static final int SUCCESS_CODE = 0;

    /** 请求状态码，0表示识别成功，非0表示识别失败（由Parser从二进制帧头设置） */
    private int code;

    /** 会话事件类型标识（由Parser从二进制帧头设置） */
    private int event;

    /** 是否为最后一个响应包（由Parser从二进制帧头flags位2设置） */
    private boolean isLastPackage;

    /** 响应数据包的序号（由Parser从二进制帧头设置） */
    private int payloadSequence;

    /** 响应数据载荷的字节大小（由Parser从二进制帧头设置） */
    private int payloadSize;

    /** 音频相关信息 */
    @JsonProperty("audio_info")
    private AudioInfo audioInfo;

    /** 识别结果 */
    @JsonProperty("result")
    private Result result;


    // ==================== 内嵌实体类 ====================

    /**
     * 音频相关信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AudioInfo {

        /** 音频时长（毫秒） */
        @JsonProperty("duration")
        private int duration;
    }

    /**
     * 识别结果，识别成功后返回
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        /** 扩展信息（含log_id等） */
        @JsonProperty("additions")
        private ResultAdditions additions;

        /** 音频识别结果文本，识别成功后返回 */
        @JsonProperty("text")
        private String text;

        /** 语音分句信息，show_utterances=true且识别成功时返回 */
        @JsonProperty("utterances")
        private List<Utterance> utterances;
    }

    /**
     * 语音分句信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Utterance {

        /** 扩展属性（含情绪、语种、语速、音量等检测扩展字段） */
        @JsonProperty("additions")
        private UtteranceAdditions additions;

        /** 当前分句结果是否为最终确定结果，true表示该分句不再变化 */
        @JsonProperty("definite")
        private boolean definite;

        /** 分句结束时间戳（毫秒） */
        @JsonProperty("end_time")
        private int endTime;

        /** 分句起始时间戳（毫秒） */
        @JsonProperty("start_time")
        private int startTime;

        /** 分句文本内容 */
        @JsonProperty("text")
        private String text;

        /** 分词信息列表，show_utterances=true且识别成功时返回 */
        @JsonProperty("words")
        private List<Word> words;
    }

    /**
     * 分词信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Word {

        /** 起始时间（毫秒） */
        @JsonProperty("start_time")
        private int startTime;

        /** 结束时间（毫秒） */
        @JsonProperty("end_time")
        private int endTime;

        /** 语音文本内容 */
        @JsonProperty("text")
        private String text;
    }

    /**
     * Result级扩展信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultAdditions {

        /** 识别日志ID */
        @JsonProperty("log_id")
        private String logId;
    }

    /**
     * Utterance级扩展属性，包含开启情绪/性别/语速/音量检测后的返回字段
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UtteranceAdditions {

        /** 说话人年龄 */
        @JsonProperty("age")
        private String age;

        /** 情绪标签 */
        @JsonProperty("emotion")
        private String emotion;

        /** 情绪强度 */
        @JsonProperty("emotion_degree")
        private String emotionDegree;

        /** 情绪强度分数 */
        @JsonProperty("emotion_degree_score")
        private String emotionDegreeScore;

        /** 情绪置信度分数 */
        @JsonProperty("emotion_score")
        private String emotionScore;

        /** 性别标签 */
        @JsonProperty("gender")
        private String gender;

        /** 性别置信度分数 */
        @JsonProperty("gender_score")
        private String genderScore;

        /** 语速（token/s） */
        @JsonProperty("speech_rate")
        private String speechRate;

        /** 音量（dB） */
        @JsonProperty("volume")
        private String volume;

        /** 识别来源 */
        @JsonProperty("source")
        private String source;

        /** 说话人ID */
        @JsonProperty("speaker_id")
        private String speakerId;

        /** 固定前缀结果修正 */
        @JsonProperty("fixed_prefix_result")
        private String fixedPrefixResult;

        /** 调用类型 */
        @JsonProperty("invoke_type")
        private String invokeType;

        /** 是否使用BigASR后处理 */
        @JsonProperty("use_bigasr_post_process")
        private String useBigasrPostProcess;

        /** 匹配的热词 */
        @JsonProperty("all_matched_hotwords")
        private String allMatchedHotwords;
    }
}