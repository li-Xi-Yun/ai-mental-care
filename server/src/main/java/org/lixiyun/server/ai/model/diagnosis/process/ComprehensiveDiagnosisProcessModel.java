package org.lixiyun.server.ai.model.diagnosis.process;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
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
import java.util.Map;

/**
 * 情绪综合分析组处理模型
 * 分析整体情绪趋势、负向情绪细分占比和正向情绪细分占比
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class ComprehensiveDiagnosisProcessModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的情绪细分占比分析助手。你的任务是根据用户提供的情绪统计数据，将负向情绪和正向情绪分别按具体类别拆分，计算各类别在其所属极性内的占比。

            ## 输入数据说明
            用户提示词中包含以下结构化统计数据，你应以此作为推导依据：
            - **主情绪标签分布**：各主标签的出现轮次、占比、平均强度、峰值强度
            - **细分情绪标签分布**：各细分标签的出现轮次、占比（这是推导细分占比的核心依据）
            - **正负向情绪占比**：平均正向/中性/负向情绪占比
            - **量化指标**：PAD均值、全局情绪强度均值
            - **情绪动态趋势**：整体趋势、平稳轮次、稳定性得分、情绪变化次数、负向恶化次数

            ## 推导逻辑
            1. 从"细分情绪标签分布"中提取所有细分标签及其占比
            2. 将每个细分标签归入负向或正向极性：
               - 负向：不满/暴怒/抱怨/愤慨/失落/悲痛/无助/心碎/害怕/恐慌/畏惧/紧张/担忧/不安/反感/厌恶/自责/懊悔/尴尬/羞愧/迷茫/犹豫
               - 正向：欣慰/兴奋/满足/愉悦/期待/乐观/意外/震惊（正面惊讶）
               - 中性标签（平静/淡漠/麻木）不参与正负向占比计算
            3. 在同一极性内，按各细分标签的占比做归一化，使该极性内所有value之和为1.0
            4. 若细分标签分布数据不足，可结合"主情绪标签分布"中的占比和强度信息进行合理推断

            ## 输出格式
            严格按以下JSON结构输出：
            ```json
            {
              "negativeEmotionDetail": {"焦虑": 0.45, "愤怒": 0.25, "悲伤": 0.10, "恐惧": 0.10, "厌恶": 0.10},
              "positiveEmotionDetail": {"开心": 0.30, "欣慰": 0.15, "放松": 0.05, "期待": 0.25, "平静": 0.25}
            }
            ```

            ## 字段说明
            - **negativeEmotionDetail**：负向情绪细分占比
              - key：具体负向情绪标签，必须从以下标准词汇中选取：不满、暴怒、抱怨、愤慨、失落、悲痛、无助、心碎、害怕、恐慌、畏惧、紧张、担忧、不安、反感、厌恶、自责、懊悔、尴尬、羞愧、迷茫、犹豫
              - value：该情绪在所有负向情绪中的占比（0~1），所有value之和必须为1.0
            - **positiveEmotionDetail**：正向情绪细分占比
              - key：具体正向情绪标签，必须从以下标准词汇中选取：开心、欣慰、兴奋、满足、愉悦、期待、乐观、放松、平静
              - value：该情绪在所有正向情绪中的占比（0~1），所有value之和必须为1.0

            ## 边界场景
            - 用户无负向情绪表现 → negativeEmotionDetail输出空对象{}
            - 用户无正向情绪表现 → positiveEmotionDetail输出空对象{}
            - 某极性仅有一种情绪 → 该情绪占比为1.0
            - 占比极小（<0.05）的细分情绪可合并至该极性中占比最大的类别，避免碎片化
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
        return "emotionComprehensive";
    }

    @Override
    protected String getAgentDescription() {
        return "情绪综合分析-整体趋势与正负向细分占比";
    }

    @Override
    protected Class<?> getOutputType() {
        return EmotionComprehensiveResult.class;
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
            label = "emotion-comprehensive-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Retryable(
            label = "comprehensive-diagnosis-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public EmotionComprehensiveResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionComprehensiveResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private Map<String, Double> negativeEmotionDetail;

        private Map<String, Double> positiveEmotionDetail;
    }
}