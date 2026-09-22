package org.lixiyun.server.ai.model.conversation;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.model.BaseModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-08-10 09:32
 */
@Slf4j
@Component
public class EmotionRecognitionModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 1024;

//    private final String userInputContextPrompt = PromptUtil.getPrompt(EmotionConstant.USER_INPUT_CONTEXT);
//    private final String standardPrompt = PromptUtil.getPrompt(EmotionConstant.STANDARD_ANALYSIS_OF_YOUTH_CONTEXTUAL_EMOTIONS);
//    private final String briefPrompt = PromptUtil.getPrompt(EmotionConstant.BRIEF_ANALYSIS_OF_YOUTH_CONTEXTUAL_EMOTIONS);

    private final String defaultSystemPrompt = """
            你是一个情绪识别分析器，负责在多轮心理陪伴对话中，对用户当前轮次的情绪状态进行结构化分析。

            【分析对象】
            只分析用户（而非AI）的情绪状态。聚焦用户在本轮消息中表达的情绪，结合历史上下文判断变化趋势。

            【分析流程】
            1. 提取情绪线索：从用户本轮消息中识别情绪关键词、语气、表述方式
            2. 判断主标签与细分：确定最突出的情绪类别及其细分维度
            3. 量化评估：对置信度、强度、PAD分数、情绪占比进行客观打分
            4. 趋势判断：对比历史情绪分析数据，判断较上一轮的变化趋势

            【核心约束】
            - 所有分析必须严格基于对话原文，严禁编造用户未表达的情绪或事件
            - 若用户消息情绪模糊或信息不足，置信度应相应降低，不要强行判断
            - 各数值字段必须客观合理，反映真实情绪状态

            【字段定义与输出规范】
            按以下JSON结构输出，共12个字段：

            - analysisContent：对用户当前情绪状态的文字分析，包括情绪触发原因、表现特征、与上下文的关联（50-150字）
            - emotionLabel：情感主标签，从以下枚举中选择：
              anger（愤怒）、sadness（悲伤）、fear（恐惧）、anxiety（焦虑）、
              disgust（厌恶）、surprise（惊讶）、happy（开心）、neutral（中性）、
              guilt（内疚）、shame（羞耻）、hope（希望）、confusion（困惑）
            - emotionSubLabel：情感细分标签，对主标签进一步细分，参考：
              anger→不满/暴怒/抱怨/愤慨  sadness→失落/悲痛/无助/心碎  fear→害怕/恐慌/畏惧
              anxiety→紧张/担忧/恐慌/不安  happy→欣慰/兴奋/满足/愉悦  neutral→平静/淡漠/麻木
              guilt→自责/懊悔  shame→尴尬/羞愧  hope→期待/乐观  confusion→迷茫/犹豫
              disgust→反感/厌恶  surprise→意外/震惊
            - emotionConfidence：识别置信度[0,1]，对当前标签判断的确定程度。信息不足时取低值
            - emotionIntensity：情绪强烈程度[0,1]，0=极微弱，1=极度强烈
            - emotionTrend：较上一轮变化趋势，从以下枚举中选择：
              escalating（升级）、deescalating（缓和）、stable（稳定）、fluctuating（波动）、initial（首轮无对比）
            - pScore：PAD愉悦度P[-1,1]，正=愉悦，负=不愉悦。参考：开心≈0.6，悲伤≈-0.6，愤怒≈-0.5，中性≈0
            - aScore：PAD唤醒度A[-1,1]，正=激动，负=平静。参考：暴怒≈0.7，平静≈-0.5，焦虑≈0.4，中性≈-0.2
            - dScore：PAD支配度D[-1,1]，正=主导/掌控，负=顺从/无力。参考：愤怒≈0.3，无助≈-0.5，中性≈0
            - negativeEmotionRatio：负向情绪占比[0,1]
            - neutralEmotionRatio：中性情绪占比[0,1]
            - positiveEmotionRatio：正向情绪占比[0,1]
              三者之和必须等于1

            【输出格式】
            直接输出JSON对象，不要用Markdown代码块包裹，不要添加任何额外文字：
            {
              "analysisContent": "用户因连续加班感到工作压力巨大，情绪以焦虑为主伴有无力感，与上轮相比焦虑有所升级",
              "emotionLabel": "anxiety",
              "emotionSubLabel": "担忧",
              "emotionConfidence": 0.88,
              "emotionIntensity": 0.72,
              "emotionTrend": "escalating",
              "pScore": -0.5,
              "aScore": 0.4,
              "dScore": -0.4,
              "negativeEmotionRatio": 0.7,
              "neutralEmotionRatio": 0.2,
              "positiveEmotionRatio": 0.1
            }
            """;

    @Override
    protected String getSystemPrompt(AiNodeConfig config) {
        if (config != null && config.getSystemPrompt() != null) {
            return config.getSystemPrompt();
        }
        return defaultSystemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "emotionRecognition";
    }

    @Override
    protected String getAgentDescription() {
        return "情感识别";
    }

    @Override
    protected Class<?> getOutputType() {
        return EmotionRecognitionResult.class;
    }

    @Override
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getOllamaModelName() != null ? config.getOllamaModelName() : defaultOllamaModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.2;
        double topP = config != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 30;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;

        return OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .numPredict(maxToken)
                .repeatPenalty(1.2)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .repeatLastN(50)
                .numCtx(maxToken)
                .numThread(Runtime.getRuntime().availableProcessors())
                .format(isJsonResponseFormat(config) ? "json" : null)
                .truncate(true)
                .seed(42)
                .mirostat(2)
                .mirostatTau(3.0f)
                .mirostatEta(0.05f)
                .useMLock(false)
                .useMMap(true)
                .numGPU(-1)
                .lowVRAM(false)
                .build();
    }

    @Override
    protected ChatOptions buildDashScopeCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDashscopeModelName() != null ? config.getDashscopeModelName() : defaultDashscopeModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.2;
        double topP = config != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 40;

        return DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .seed(42)
                .maxToken(maxToken)
                .repetitionPenalty(1.2)
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(isJsonResponseFormat(config) ? DashScopeResponseFormat.Type.JSON_OBJECT : DashScopeResponseFormat.Type.TEXT)
                        .build())
                .enableThinking(true)
                .thinkingBudget(5)
                .enableSearch(false)
                .stream(false)
                .incrementalOutput(false)
                .multiModel(false)
                .vlHighResolutionImages(false)
                .build();
    }

    @Override
    protected ChatOptions buildDeepSeekCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDeepseekModelName() != null ? config.getDeepseekModelName() : defaultDeepseekModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.2;
        double topP = config != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;

        return DeepSeekChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxToken)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .responseFormat(ResponseFormat.builder()
                        .type(isJsonResponseFormat(config) ? ResponseFormat.Type.JSON_OBJECT : ResponseFormat.Type.TEXT)
                        .build())
                .logprobs(false)
                .topLogprobs(null)
                .build();
    }

    @Override
    protected ChatOptions buildDefaultCompanionOptions(AiNodeConfig config) {
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.4;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return ChatOptions.builder()
                .topK(40)
                .topP(0.9)
                .frequencyPenalty(0.6)
                .presencePenalty(0.2)
                .temperature(temperature)
                .maxTokens(maxToken)
                .build();
    }

    @Retryable(
            label = "emotion-recognition-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return call(chatModel, userPrompt, config, null);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return stream(chatModel, userPrompt, config, null);
    }

    @Retryable(
            label = "emotion-recognition-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public EmotionRecognitionResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config, runnableConfig);
    }

    public EmotionRecognitionResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return callForResult(chatModel, userPrompt, config, null);
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EmotionRecognitionResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String analysisContent;

        private String emotionLabel;

        private String emotionSubLabel;

        private Double emotionConfidence;

        private Double emotionIntensity;

        private String emotionTrend;

        @JsonProperty("pScore")
        private Double pScore;

        @JsonProperty("aScore")
        private Double aScore;

        @JsonProperty("dScore")
        private Double dScore;

        private Double negativeEmotionRatio;

        private Double neutralEmotionRatio;

        private Double positiveEmotionRatio;

        public static String getPrompt() {
            return "analysisContent：情感分析详情，" +
                    "emotionLabel：情感标签（如anger/开心/neutral/不满等），" +
                    "emotionSubLabel：情感细分标签（如愤怒可细分\"不满/暴怒/抱怨\"），" +
                    "emotionConfidence：情感识别置信度（0-1，如0.9200），" +
                    "emotionIntensity：情绪本身的强烈程度（0-1，如0.9200），" +
                    "emotionTrend：较上一轮的情绪变化趋势，" +
                    "pScore：PAD愉悦度P，取值范围[-1,1]，" +
                    "aScore：PAD唤醒度A，取值范围[-1,1]，" +
                    "dScore：PAD支配度D，取值范围[-1,1]，" +
                    "negativeEmotionRatio：负向情绪占比（0-1），" +
                    "neutralEmotionRatio：中性情绪占比（0-1），" +
                    "positiveEmotionRatio：正向情绪占比（0-1）";
        }
    }

}