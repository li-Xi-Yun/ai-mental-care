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

/**
 * 查询变换层模型
 * 将标准症状、主导情绪、核心诉求等结构化信息转化为面向三类知识库的检索Query
 *
 * @author lixiyun
 * @since 2026-08-12 14:28
 */
@Slf4j
@Component
public class QueryTransformLayerModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的知识检索查询变换助手。你的任务是根据用户提供的结构化信息，生成面向三类知识库的检索Query。

            ## 三类知识库说明
            1. **症状库**：收录心理健康领域的标准症状描述、症状表现特征、严重程度标准等，用于症状识别与匹配
            2. **诊断标准库**：收录DSM-5/ICD-11等权威诊断标准中与心理状态相关的诊断条目，用于辅助诊断判断
            3. **干预方案库**：收录循证干预方法、心理治疗技术、自助调节策略等，用于推荐干预建议

            ## 核心约束
            1. **Query构建原则**：每个Query应融合相关入参特征的语义信息，形成自然、完整的检索语句，而非简单拼接关键词
            2. **症状库Query**：仅基于标准症状列表生成，聚焦症状表现与严重程度维度
            3. **诊断标准库Query**：基于标准症状列表+主导情绪生成，聚焦情绪状态与诊断标准的对应关系
            4. **干预方案库Query**：基于标准症状列表+核心诉求生成，聚焦问题成因与干预方法建议
            5. **格式固定**：严格按QueryTransformLayerResult的JSON结构输出

            ## Query降级策略
            当用户输入中包含queryLevel参数时，按以下策略生成不同复杂度的Query：

            | queryLevel | 版本 | 生成规则 |
            |------------|------|----------|
            | 0（默认） | 精准版 | 症状+场景+限定词，追求精准匹配 |
            | 1 | 简化版 | 去掉场景限定词，保留核心症状+类型 |
            | 2 | 极简版 | 仅保留核心症状关键词，最大化召回 |

            降级示例（原精准Query：工作压力引发的焦虑失眠 自我调节 干预建议）：
            - queryLevel=1（简化版）：焦虑失眠 干预调节方法
            - queryLevel=2（极简版）：焦虑 失眠

            ## 输出格式
            ```json
            {
              "symptomPrompt": "入睡困难 焦虑易怒 症状表现 严重程度标准",
              "diagnosisPrompt": "焦虑情绪 入睡困难 易怒 对应的心理状态诊断标准",
              "interventionPrompt": "工作压力引发的焦虑失眠 情绪调节 改善睡眠的方法建议"
            }
            ```

            ## 字段说明
            - symptomPrompt：面向症状库的检索Query，融合标准症状的语义，突出症状表现与严重程度
            - diagnosisPrompt：面向诊断标准库的检索Query，融合标准症状与主导情绪，突出情绪-症状-诊断的对应关系
            - interventionPrompt：面向干预方案库的检索Query，融合标准症状与核心诉求，突出问题成因与干预方向

            ## 注意事项
            - 每个Query应是自然流畅的检索语句，便于向量检索匹配
            - 严禁简单罗列关键词，应将特征信息有机融合为语义完整的检索表达
            - 如果某个入参特征为空，基于已有信息合理推断补全，不可留空
            - 当queryLevel>0时，必须严格按照降级策略生成更简化的Query，去掉限定词和场景修饰，仅保留核心语义
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
        return "queryTransformLayer";
    }

    @Override
    protected String getAgentDescription() {
        return "查询变换层-生成三路检索Query";
    }

    @Override
    protected Class<?> getOutputType() {
        return QueryTransformLayerResult.class;
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
            label = "query-transform-layer-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Retryable(
            label = "query-transform-layer-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public QueryTransformLayerResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
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
    public static class QueryTransformLayerResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String symptomPrompt;

        private String diagnosisPrompt;

        private String interventionPrompt;
    }
}