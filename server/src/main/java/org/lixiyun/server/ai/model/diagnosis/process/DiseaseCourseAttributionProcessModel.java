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

/**
 * 病程归因组处理模型
 * 从对话中提取核心触发场景、触发关键词、症状持续时长、发作模式等病程归因信息
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class DiseaseCourseAttributionProcessModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的病程归因分析助手。你的任务是根据用户的对话内容，提取病程归因相关的结构化信息。

            ## 核心约束
            1. **场景识别**：从对话中识别用户核心触发场景，如工作压力、人际关系、家庭矛盾等
            2. **关键词提取**：提取与触发场景密切相关的关键词，多个关键词用逗号分隔
            3. **轮次定位**：准确判断核心触发因素首次出现的对话轮次
            4. **时长推断**：根据用户描述推断症状持续时长，使用标准化的时长表述
            5. **发作模式**：判断症状的发作模式，从"持续性/阵发性/偶发/逐渐加重/反复波动"中选择
            6. **格式固定**：严格按DiseaseCourseAttributionResult的JSON结构输出

            ## 输出格式
            ```json
            {
              "coreTriggerScene": "工作压力",
              "coreTriggerKeywords": "加班,绩效,失业",
              "triggerRoundNum": 2,
              "symptomDuration": "1-2周",
              "onsetPattern": "持续性",
              "firstTriggerDesc": "用户提到近期因项目截止日期临近，连续加班两周，感到巨大压力"
            }
            ```

            ## 字段说明
            - coreTriggerScene：核心触发场景，如"工作压力/人际关系/家庭矛盾/学业压力/经济压力/健康问题/其他"
            - coreTriggerKeywords：核心触发关键词，多个用逗号分隔
            - triggerRoundNum：首次出现核心触发因素的轮次（从1开始计数）
            - symptomDuration：症状持续时长，从"几天/1-2周/1个月以上/3个月以上/半年以上"中选择最接近的
            - onsetPattern：发作模式，从"持续性/阵发性/偶发/逐渐加重/反复波动"中选择
            - firstTriggerDesc：用户提及的首次触发事件/原因的自然语言描述

            ## 注意事项
            - 如果对话中未明确提及某项信息，根据上下文合理推断，不可留空
            - triggerRoundNum必须为正整数
            - 严禁编造用户未提及的信息，推断需有依据
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
        return "diseaseCourseAttribution";
    }

    @Override
    protected String getAgentDescription() {
        return "病程归因组-提取触发场景与病程特征";
    }

    @Override
    protected Class<?> getOutputType() {
        return DiseaseCourseAttributionResult.class;
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
            label = "disease-course-attribution-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Retryable(
            label = "disease-course-attribution-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public DiseaseCourseAttributionResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
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
    public static class DiseaseCourseAttributionResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String coreTriggerScene;

        private String coreTriggerKeywords;

        private Integer triggerRoundNum;

        private String symptomDuration;

        private String onsetPattern;

        private String firstTriggerDesc;
    }
}