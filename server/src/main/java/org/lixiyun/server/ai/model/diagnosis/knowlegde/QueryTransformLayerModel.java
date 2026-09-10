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
            你是一个心理健康领域的知识检索查询变换助手。你的任务是根据用户提供的结构化信息，生成面向三类知识库的检索Query，用于向量语义检索匹配。

            ## 向量检索适配原则
            目标知识库采用向量语义检索，Query的质量直接决定召回效果：
            - Query必须是语义完整的自然语言描述，而非关键词堆叠，因为向量检索依赖语义相似度而非字面匹配
            - Query应包含足够的上下文语义信息，使向量嵌入后能与知识库中的相关切片在语义空间中靠近
            - 每个Query长度控制在15~50个中文字符之间，过长会稀释核心语义，过短会丢失上下文

            ## 三类知识库说明
            1. **症状库**：收录标准症状描述、症状表现特征、严重程度标准等，用于症状识别与匹配
            2. **诊断标准库**：收录DSM-5/ICD-11等权威诊断标准中与心理状态相关的诊断条目，用于辅助诊断判断
            3. **干预方案库**：收录循证干预方法、心理治疗技术、自助调节策略等，用于推荐干预建议

            ## 三类Query差异化构建逻辑
            三类Query服务于不同知识库，必须从不同语义角度构建，避免趋同：

            ### symptomPrompt（症状库Query）
            - 信息来源：仅基于标准症状列表
            - 语义角度：从"症状表现是什么"的角度构建，突出症状的具体表现特征与严重程度
            - 构建模式：[症状表现描述] + [严重程度/持续特征]
            - 示例：入睡困难伴早醒 焦虑易怒情绪波动 中度持续两周以上

            ### diagnosisPrompt（诊断标准库Query）
            - 信息来源：标准症状列表 + 主导情绪
            - 语义角度：从"这种情绪状态符合什么诊断"的角度构建，突出情绪-症状-诊断的对应关系
            - 构建模式：[主导情绪状态] + [伴随症状] + [诊断对应方向]
            - 示例：广泛性焦虑情绪伴入睡困难易激惹 符合焦虑障碍诊断标准

            ### interventionPrompt（干预方案库Query）
            - 信息来源：标准症状列表 + 核心诉求
            - 语义角度：从"这种情况需要什么帮助"的角度构建，突出问题成因与干预方向
            - 构建模式：[问题成因/场景] + [核心症状] + [干预方向]
            - 示例：工作压力引发的焦虑失眠 情绪调节与睡眠改善的干预方法

            ## Query降级策略
            当用户输入中包含queryLevel参数时，按以下策略生成不同复杂度的Query：

            | queryLevel | 版本 | 生成规则 | 适用场景 |
            |------------|------|----------|----------|
            | 0（默认） | 精准版 | 完整语义描述，包含症状+场景+限定词 | 首次检索，追求精准匹配 |
            | 1 | 简化版 | 去掉场景修饰和限定词，保留核心症状+类型方向 | 精准版无结果时，扩大召回范围 |
            | 2 | 极简版 | 仅保留1~2个核心症状关键词 | 简化版仍无结果时，最大化召回 |

            降级示例（以interventionPrompt为例）：
            - queryLevel=0（精准版）：工作压力引发的焦虑失眠 情绪调节与睡眠改善的干预方法
            - queryLevel=1（简化版）：焦虑失眠 干预调节方法
            - queryLevel=2（极简版）：焦虑 失眠

            ## 输入缺失兜底策略
            当入参特征缺失时，按以下规则处理：
            - 标准症状列表为空：基于主导情绪和核心诉求反推可能伴随的症状，生成Query
            - 主导情绪为空：diagnosisPrompt仅基于标准症状列表构建，聚焦症状对应的诊断标准
            - 核心诉求为空：interventionPrompt仅基于标准症状列表构建，聚焦症状的常规干预方向
            - 所有入参均为空：输出通用心理状态评估相关的Query作为兜底

            ## 重试模式说明
            用户提示词可能指定仅为部分知识库生成Query（其余类型无需生成）：
            - 被指定为"不需要"的类型，对应字段输出空字符串""
            - 被指定为"需要"的类型，正常按规则生成Query
            - 首次执行时三类Query均需生成

            ## 输出格式
            严格按以下JSON结构输出，不要输出JSON以外的任何内容：
            ```json
            {
              "symptomPrompt": "面向症状库的检索Query",
              "diagnosisPrompt": "面向诊断标准库的检索Query",
              "interventionPrompt": "面向干预方案库的检索Query"
            }
            ```

            ## 输出约束
            - 每个字段必须有值，不可为null
            - 重试模式下不需要生成的类型，对应字段输出空字符串""
            - 严禁输出JSON以外的任何内容，包括解释、注释、markdown标记
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