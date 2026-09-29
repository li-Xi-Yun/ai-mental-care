package org.lixiyun.common.agent.asr.vendor.volcengine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.lixiyun.common.json.utils.JsonUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * 火山引擎ASR请求可选参数配置，从asr-volcengine-request.yml中读取
 */
@Data
@Component
@ConfigurationProperties(prefix = "asr.volcengine.request")
@PropertySource(value = "classpath:asr-volcengine-request.yml", factory = YamlPropertySourceFactory.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsrRequestProperties {

    /** 模型名称（bigmodel/streaming/bigmodel_new等） */
    @JsonProperty("model_name")
    private String modelName = "bigmodel";

    /** 是否启用非流式模式 */
    @JsonProperty("enable_nonstream")
    private boolean enableNonstream = false;

    /** 是否启用说话人识别 */
    @JsonProperty("enable_speaker_info")
    private boolean enableSpeakerInfo = false;

    /** 是否启用ITN（逆文本正则化） */
    @JsonProperty("enable_itn")
    private boolean enableItn = true;

    /** 是否启用标点预测 */
    @JsonProperty("enable_punc")
    private boolean enablePunc = true;

    /** 是否启用DDC（人声音质增强检测） */
    @JsonProperty("enable_ddc")
    private boolean enableDdc = false;

    /** 中文特殊方言文本转换（目前支持shaanxi） */
    @JsonProperty("output_zh_variant")
    private String outputZhVariant;

    /** 是否展示分句信息 */
    @JsonProperty("show_utterances")
    private boolean showUtterances = false;

    /** 是否展示语速信息 */
    @JsonProperty("show_speech_rate")
    private boolean showSpeechRate = false;

    /** 是否展示音量信息 */
    @JsonProperty("show_volume")
    private boolean showVolume = false;

    /** 是否启用语种检测 */
    @JsonProperty("enable_lid")
    private boolean enableLid = false;

    /** 是否启用情绪检测 */
    @JsonProperty("enable_emotion_detection")
    private boolean enableEmotionDetection = false;

    /** 是否启用性别检测 */
    @JsonProperty("enable_gender_detection")
    private boolean enableGenderDetection = false;

    /** 是否启用年龄检测 */
    @JsonProperty("enable_age_detection")
    private boolean enableAgeDetection = false;

    /** 结果返回类型（full/single） */
    @JsonProperty("result_type")
    private String resultType = "single";

    /** 是否启用大模型语义润色 */
    @JsonProperty("enable_accelerate_text")
    private boolean enableAccelerateText = false;

    /** 大模型语义润色分数阈值 0~100 */
    @JsonProperty("accelerate_score")
    private int accelerateScore = 0;

    /** VAD切句时长 ms */
    @JsonProperty("vad_segment_duration")
    private int vadSegmentDuration = 3000;

    /** 静音事件VAD结束窗口大小 ms */
    @JsonProperty("end_window_size")
    private int endWindowSize = 800;

    /** 强制转语音等待时间 ms */
    @JsonProperty("force_to_speech_time")
    private int forceToSpeechTime = 0;

    /** 敏感词过滤配置JSON字符串 */
    @JsonProperty("sensitive_words_filter")
    private String sensitiveWordsFilter;

    /** 是否启用POI-Match兴趣点匹配 */
    @JsonProperty("enable_poi_fc")
    private boolean enablePoiFc = false;

    /**
     * 将请求参数序列化为JSON字符串
     *
     * @return request节点的JSON字符串
     */
    public String toRequestJson() {
        return JsonUtils.toJsonString(this);
    }
}