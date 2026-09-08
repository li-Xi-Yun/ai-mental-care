package org.lixiyun.server.ai.model.diagnosis.knowlegde;

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
 * 重排层模型
 * 对向量召回的粗结果做二次校验，基于大模型语义判断筛选高相关切片
 *
 * @author lixiyun
 * @since 2026-08-12 14:28
 */
@Slf4j
@Component
public class RerankLayerModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 4096;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的知识重排打分助手。你的任务是对向量检索召回的知识切片做二次语义校验，为每个切片评估与用户症状和诉求的相关性分值。

            ## 核心职责
            1. **语义相关性打分**：为每个候选切片评估与用户情况的相关性，输出0~1区间的分值，0表示完全无关，1表示高度相关
            2. **区分度打分**：不同切片之间应有明显分值差异，最相关的切片应接近1.0，不相关的应接近0.0

            ## 三类知识库的打分标准
            1. **症状库**：切片内容与用户标准症状的语义关联程度，描述的症状表现是否与用户情况匹配
            2. **诊断标准库**：切片内容与用户症状+情绪状态对应的诊断条目相关程度，能否辅助判断用户心理状态
            3. **干预方案库**：切片内容与用户核心诉求的语义关联程度，能否提供针对性的干预方法或调节策略

            ## 打分约束
            - 仅对与用户情况确实相关的切片给予高分（≥0.5），不相关的切片给予低分（<0.5）
            - 如果切片内容与用户症状/诉求完全无关，给予0分
            - 同一知识库内高度相似的切片，只对最相关的那条给高分，其余降分
            - 分值应体现差异：最相关0.8~1.0，中等相关0.5~0.8，低相关0.2~0.5，无关0~0.2

            ## 输出格式
            严格按以下JSON结构输出，key为切片ID（字符串），value为相关性分值（0~1浮点数）：
            ```json
            {
              "symptomScores": {"101": 0.9, "102": 0.6, "103": 0.2},
              "diagnosisScores": {"201": 0.85, "202": 0.3},
              "interventionScores": {"301": 0.8, "302": 0.5, "303": 0.1}
            }
            ```

            ## 注意事项
            - 切片ID必须从输入的候选切片列表中选取，不可自行编造
            - 每个候选切片都必须给出分值，不要遗漏
            - 如果某类知识库中没有高相关切片，对应对象输出为空{}
            - 严格按JSON格式输出，不要输出任何其他内容
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
        return "rerankLayer";
    }

    @Override
    protected String getAgentDescription() {
        return "重排层-语义筛选去噪与相关性排序";
    }

    @Override
    protected Class<?> getOutputType() {
        return RerankLayerResult.class;
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
            label = "rerank-layer-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Retryable(
            label = "rerank-layer-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public RerankLayerResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
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
    public static class RerankLayerResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private Map<Long, Double> symptomScores;
        private Map<Long, Double> diagnosisScores;
        private Map<Long, Double> interventionScores;
    }
}