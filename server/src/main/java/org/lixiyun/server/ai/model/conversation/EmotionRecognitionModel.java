package org.lixiyun.server.ai.model.conversation;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.prompt.constant.EmotionConstant;
import org.lixiyun.common.agent.prompt.utils.PromptUtil;
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

    private final String userInputContextPrompt = PromptUtil.getPrompt(EmotionConstant.USER_INPUT_CONTEXT);
    private final String standardPrompt = PromptUtil.getPrompt(EmotionConstant.STANDARD_ANALYSIS_OF_YOUTH_CONTEXTUAL_EMOTIONS);
    private final String briefPrompt = PromptUtil.getPrompt(EmotionConstant.BRIEF_ANALYSIS_OF_YOUTH_CONTEXTUAL_EMOTIONS);

    private final String defaultSystemPrompt = """
            你是一个专业的心理健康领域情感识别助手。你的任务是基于用户与AI的心理咨询对话上下文，对用户当前轮次的情绪状态进行多维度结构化分析。

            ## 核心约束
            1. **基于原文**：所有分析必须严格基于对话原文内容，严禁编造用户未表达的情绪或事件。
            2. **客观量化**：置信度、强度、PAD分数、情绪占比等数值字段必须客观合理，反映真实情绪状态。
            3. **格式固定**：按EmotionRecognitionResult的JSON结构输出，包含analysisContent、emotionLabel、emotionSubLabel、emotionConfidence、emotionIntensity、emotionTrend、pScore、aScore、dScore、negativeEmotionRatio、neutralEmotionRatio、positiveEmotionRatio共12个字段。

            ## 字段说明
            - **analysisContent**：情感分析详情，对用户当前情绪状态的文字化分析描述，包括情绪触发原因、表现特征、与上下文的关联等。
            - **emotionLabel**：情感主标签，从以下集合中选择：anger（愤怒）、sadness（悲伤）、fear（恐惧）、anxiety（焦虑）、disgust（厌恶）、surprise（惊讶）、happy（开心）、neutral（中性）、guilt（内疚）、shame（羞耻）、hope（希望）、confusion（困惑）。
            - **emotionSubLabel**：情感细分标签，对主标签的进一步细分。如anger可细分为"不满/暴怒/抱怨"，sadness可细分为"失落/悲痛/无助"，anxiety可细分为"紧张/担忧/恐慌"，happy可细分为"欣慰/兴奋/满足"。
            - **emotionConfidence**：情感识别置信度，取值范围[0,1]，表示对当前情感标签判断的确定程度。
            - **emotionIntensity**：情绪本身的强烈程度，取值范围[0,1]，0表示情绪极微弱，1表示情绪极度强烈。
            - **emotionTrend**：较上一轮的情绪变化趋势，从以下集合中选择：escalating（升级）、deescalating（缓和）、stable（稳定）、fluctuating（波动）、initial（首轮无对比）。
            - **pScore**：PAD情感模型愉悦度P（Pleasure），取值范围[-1,1]，正值表示愉悦，负值表示不愉悦。
            - **aScore**：PAD情感模型唤醒度A（Arousal），取值范围[-1,1]，正值表示高唤醒（激动），负值表示低唤醒（平静）。
            - **dScore**：PAD情感模型支配度D（Dominance），取值范围[-1,1]，正值表示主导/掌控，负值表示顺从/无力。
            - **negativeEmotionRatio**：负向情绪占比，取值范围[0,1]，当前情绪中负面成分的比重。
            - **neutralEmotionRatio**：中性情绪占比，取值范围[0,1]，当前情绪中中性成分的比重。
            - **positiveEmotionRatio**：正向情绪占比，取值范围[0,1]，当前情绪中正面成分的比重。三者之和应等于1。

            ## 输出格式
            ```json
            {
              "analysisContent": "用户情绪状态的文字化分析描述",
              "emotionLabel": "anxiety",
              "emotionSubLabel": "担忧",
              "emotionConfidence": 0.92,
              "emotionIntensity": 0.75,
              "emotionTrend": "escalating",
              "pScore": -0.6,
              "aScore": 0.5,
              "dScore": -0.3,
              "negativeEmotionRatio": 0.7,
              "neutralEmotionRatio": 0.2,
              "positiveEmotionRatio": 0.1
            }
            ```
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
        Double repeatPenalty = config != null && config.getRepeatPenalty() != null ? config.getRepeatPenalty().doubleValue() : 1.2;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;
        Integer seed = config != null ? config.getSeed() : 42;

        return OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .numPredict(maxToken)
                .repeatPenalty(repeatPenalty)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .repeatLastN(50)
                .numCtx(maxToken)
                .numThread(Runtime.getRuntime().availableProcessors())
                .format("json")
                .truncate(true)
                .seed(seed)
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
        Integer seed = config != null ? config.getSeed() : 42;

        return DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .seed(seed)
                .maxToken(maxToken)
                .repetitionPenalty(1.2)
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(DashScopeResponseFormat.Type.JSON_OBJECT)
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
                        .type(ResponseFormat.Type.JSON_OBJECT)
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
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config);
    }

    @Retryable(
            label = "emotion-recognition-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public EmotionRecognitionResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config);
    }

    @Data
    @Builder
    public static class EmotionRecognitionResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String analysisContent;

        private String emotionLabel;

        private String emotionSubLabel;

        private Double emotionConfidence;

        private Double emotionIntensity;

        private String emotionTrend;

        private Double pScore;

        private Double aScore;

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