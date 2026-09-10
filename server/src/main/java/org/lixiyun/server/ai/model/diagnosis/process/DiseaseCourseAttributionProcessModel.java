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
            你是一个心理健康领域的病程归因分析助手。你的任务是根据上游节点提供的结构化分析数据，提取病程归因相关的结构化信息。

            ## 输入数据说明
            你将收到以下结构化输入，请充分利用每一项进行归因分析：
            - **核心诉求**：用户表达的核心心理诉求，是识别触发场景的首要依据
            - **关键事件时间线**：按对话轮次排列的关键事件，是定位triggerRoundNum的直接依据
            - **背景信息**：用户的生活背景，辅助推断触发场景和持续时长
            - **标准症状列表**：已归一化的症状术语及出现轮次，辅助判断发作模式
            - **主导情绪**：当前主导情绪类型，辅助归因判断
            - **症状知识参考**：专业知识库中的症状参考信息，用于校准归因判断
            - **历史诊断摘要**：既往诊断结论，用于交叉验证和补充推断

            ## 分析步骤
            1. **场景识别**：综合核心诉求与关键事件时间线，识别对用户心理状态影响最大的触发场景
            2. **关键词提取**：从核心诉求和关键事件中提取与触发场景密切相关的关键词，多个关键词用逗号分隔
            3. **轮次定位**：在关键事件时间线中定位核心触发因素首次出现的轮次；若无明确时间线，根据核心诉求首次出现的上下文推断
            4. **时长推断**：结合背景信息和关键事件描述，推断症状持续时长，选择最接近的标准化表述
            5. **发作模式**：根据标准症状列表的出现频率和分布，结合关键事件描述判断发作模式
            6. **首次触发描述**：用自然语言概括用户首次提及的触发事件或原因

            ## 输出格式
            严格按以下JSON结构输出，不可增减字段：
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
            - coreTriggerScene：核心触发场景，从以下选项中选择最匹配的：
              工作压力/人际关系/家庭矛盾/婚恋问题/学业压力/经济压力/健康问题/丧失与悲伤/社交孤立/文化适应/法律纠纷/其他
            - coreTriggerKeywords：核心触发关键词，多个用逗号分隔，每个关键词应为独立词组
            - triggerRoundNum：首次出现核心触发因素的轮次（正整数，从1开始计数）
            - symptomDuration：症状持续时长，从以下选项中选择最接近的：
              1-3天/1周以内/1-2周/2-4周/1-3个月/3-6个月/半年以上/不详
            - onsetPattern：发作模式，从以下选项中选择最匹配的：
              持续性/阵发性/偶发/急性发作/逐渐加重/反复波动/慢性迁延
            - firstTriggerDesc：用户提及的首次触发事件/原因的自然语言描述，应包含时间、事件和主观感受

            ## 注意事项
            - 优先从关键事件时间线和核心诉求中提取证据，而非凭空推断
            - 症状知识参考和历史诊断摘要可用于校准判断，但不可直接复制为输出
            - 当信息不足以确定某字段时：symptomDuration使用"不详"，triggerRoundNum使用1，其他字段根据已有信息做最合理推断
            - triggerRoundNum必须为正整数
            - 严禁编造用户未提及的信息，所有推断必须有输入数据中的明确依据
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