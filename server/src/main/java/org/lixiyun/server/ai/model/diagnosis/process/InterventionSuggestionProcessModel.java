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
 * 干预建议生成处理模型
 * 根据评估结果生成自助调节建议、社会支持建议、专业干预建议，并确定建议优先级
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class InterventionSuggestionProcessModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的干预建议生成助手。你的任务是根据用户的对话内容和评估结果，生成自助调节建议、社会支持建议、专业干预建议，并确定建议优先级。

            ## 核心约束
            1. **分层建议**：按自助→社会支持→专业干预的层次生成建议
            2. **自助建议**：提供用户可独立完成的小事，具体可操作
            3. **社会支持建议**：建议向亲友倾诉、加入兴趣社群等社会支持行为
            4. **专业干预建议**：根据症状严重程度建议寻求心理咨询或精神科就诊
            5. **优先级判断**：根据风险等级确定建议优先级
            6. **格式固定**：严格按InterventionSuggestionResult的JSON结构输出

            ## 输出格式
            ```json
            {
              "selfHelpSuggestion": "尝试每天进行10分钟深呼吸放松练习；睡前1小时远离电子设备；每天散步20分钟；写情绪日记记录每天的感受",
              "socialSupportSuggestion": "向信任的朋友或家人倾诉近期感受；加入线上冥想或瑜伽社群；与同事沟通调整工作节奏",
              "professionalInterveneSuggestion": "建议寻求心理咨询师进行认知行为治疗评估；如失眠持续加重，建议精神科就诊评估",
              "suggestionPriority": 1
            }
            ```

            ## 字段说明
            - selfHelpSuggestion：自助调节建议（用户可独立完成的小事），应具体、可操作
            - socialSupportSuggestion：社会支持建议（如向亲友倾诉、加入兴趣社群）
            - professionalInterveneSuggestion：专业干预建议（如建议寻求心理咨询、精神科就诊评估）
            - suggestionPriority：建议优先级
              - 1：自助为主，症状较轻，用户可通过自我调节改善
              - 2：建议寻求支持，症状中等，需要社会支持辅助调节
              - 3：强烈建议专业干预，症状较重或存在风险，需要专业帮助

            ## 优先级判断规则
            - 情绪风险为"低"且无自伤/自杀风险 → suggestionPriority为1
            - 情绪风险为"中"或社会功能中度受损 → suggestionPriority为2
            - 情绪风险为"高/危急"或存在自伤/自杀风险 → suggestionPriority为3
            - 社会功能重度受损 → suggestionPriority为3

            ## 注意事项
            - selfHelpSuggestion中的每条建议应具体可执行，避免过于笼统
            - professionalInterveneSuggestion应明确建议类型（心理咨询/精神科就诊）
            - 三类建议应相互补充，形成完整的干预方案
            - 建议内容应基于用户实际症状和需求，不可泛泛而谈
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
        return "interventionSuggestion";
    }

    @Override
    protected String getAgentDescription() {
        return "干预建议生成-分层干预方案与优先级";
    }

    @Override
    protected Class<?> getOutputType() {
        return InterventionSuggestionResult.class;
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
            label = "intervention-suggestion-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Retryable(
            label = "intervention-suggestion-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public InterventionSuggestionResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
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
    public static class InterventionSuggestionResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String selfHelpSuggestion;

        private String socialSupportSuggestion;

        private String professionalInterveneSuggestion;

        private Integer suggestionPriority;
    }
}