package org.lixiyun.common.agent.asr.model;

import lombok.Builder;
import lombok.Data;
import org.lixiyun.common.agent.asr.api.AsrProviderType;

import java.util.Map;

/**
 * ASR通用业务实体类，用于标准化对接所有厂商的ASR返回数据信息
 */
@Data
@Builder
public class AsrResult {

    /** 中间结果状态码 */
    public static final Integer INTERMEDIATE = 0;
    /** 句子结束状态码 */
    public static final Integer SENTENCE_END = 1;
    /** 错误状态码 */
    public static final Integer ERROR = -1;

    /** 会话ID */
    private Long conversationId;

    /** ASR厂商 */
    private AsrProviderType providerType;

    /** 分句文本 */
    private String text;

    /** 句子编号（从1开始） */
    private int sentenceIndex;

    /** 置信度 0.0~1.0 */
    private double confidence;

    /** 分句开始时间 ms */
    private Long startTime;

    /** 分句结束时间 ms */
    private Long endTime;

    /** 情绪标签（neutral/happy/angry/sad/surprise等） */
    private String emotion;

    /** 情绪强度等级（weak/medium/strong） */
    private String emotionDegree;

    /** 情绪强度置信度 0.0~1.0，越接近1越确信 */
    private Double emotionDegreeScore;

    /** 情绪标签置信度 0.0~1.0，越接近1越确信 */
    private Double emotionScore;

    /** 说话人性别标签（male/female） */
    private String gender;

    /** 说话人性别识别置信度 0.0~1.0 */
    private Double genderScore;

    /** 说话人估算年龄，字符串类型浮点数（如"24.98"表示约25岁），仅供参考 */
    private String age;

    /** 语种标签（speech_mand/speech_en/speech_dia_cant等） */
    private String languageTag;

    /** 语速 token/s */
    private Double speechRate;

    /** 音量 dB */
    private Double volume;

    /** 状态码：0中间结果，1句子结束，-1错误 */
    private Integer resultCode;

    /** 扩展属性（厂商特有字段） */
    private Map<String, Object> extra;

    /** 是否为中间结果 */
    public boolean isIntermediate() {
        return INTERMEDIATE.equals(resultCode);
    }

    /** 是否为句子结束 */
    public boolean isSentenceEnd() {
        return SENTENCE_END.equals(resultCode);
    }
}