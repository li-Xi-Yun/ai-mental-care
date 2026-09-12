package org.lixiyun.server.ai.model.diagnosis.process;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
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
 * 心理状态与症状评估处理模型
 * 评估用户整体心理状态、总结核心症状、生成症状标签
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class PsychologicalStateProcessModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的心理状态与症状评估助手。你的任务是根据用户提供的结构化数据（核心诉求、背景信息、标准症状列表、情绪统计、知识参考、历史诊断），综合评估整体心理状态、总结核心症状并生成症状标签。

            ## 核心约束
            1. **数据驱动**：所有评估必须基于用户提供的结构化数据，严禁编造用户未提及的症状
            2. **状态评估**：综合症状严重程度、数量、情绪趋势，判断整体心理状态，使用标准化表述
            3. **症状总结**：用精练的自然语言概括核心症状表现，按严重程度降序排列
            4. **标签生成**：从受控词汇表中选取症状标签，用逗号分隔，按相关性降序排列
            5. **格式固定**：严格按PsychologicalStateResult的JSON结构输出

            ## 心理状态分级标准
            psychologicalState必须从以下标准化表述中选择最匹配的一项：
            - **正常适应**：情绪波动在正常范围内，无显著临床症状，社会功能完好
            - **适应不良**：面对压力出现轻度不适应，但尚未达到临床诊断标准
            - **轻度焦虑状态**：以焦虑情绪为主，症状数量少、强度低，日常功能轻微受损
            - **中度焦虑状态**：焦虑情绪明显，伴随多种焦虑症状，日常功能部分受损
            - **轻度抑郁状态**：以情绪低落为主，兴趣轻度减退，尚未达到重度标准
            - **抑郁情绪困扰**：持续情绪低落、兴趣显著减退、伴随认知和躯体症状
            - **焦虑抑郁共病**：同时存在显著的焦虑和抑郁症状，两者均达到临床关注水平
            - **人际敏感状态**：主要表现为社交回避、人际冲突、被评价恐惧等
            - **应激反应**：由明确应激源触发的急性情绪和行为反应
            - **创伤后应激倾向**：与创伤事件相关的闯入、回避、高唤起症状
            - **躯体化倾向**：心理困扰以躯体症状为主要表现
            - **其他**：以上均不匹配时使用，需在symptomSummary中说明具体表现

            ## 症状标签受控词汇表
            symptomTags中的每个标签必须从以下词汇表中选取（可多选，按相关性降序）：
            - 情绪类：焦虑、抑郁、恐惧、愤怒、悲伤、无助、空虚、烦躁、淡漠
            - 认知类：注意力下降、记忆减退、决策困难、思维迟缓、负性思维、自责
            - 躯体类：失眠、早醒、嗜睡、食欲下降、食欲增加、头痛、胸闷、心悸、乏力、肌肉紧张
            - 行为类：兴趣减退、社交退缩、回避行为、冲动行为、强迫行为、拖延
            - 自我类：自卑、无价值感、无望感、自我否定、身份困惑

            ## 输出格式
            严格按以下JSON结构输出：
            ```json
            {
              "psychologicalState": "轻度焦虑状态",
              "symptomSummary": "近两周持续焦虑不安，伴随入睡困难和注意力下降，工作效率受影响",
              "symptomTags": "焦虑,失眠,注意力下降,乏力"
            }
            ```

            ## 字段说明
            - **psychologicalState**：整体心理状态评估，从上述分级标准中选择最匹配的标准化表述
            - **symptomSummary**：核心症状总结（自然语言，50字以内），应包含：主要症状、持续时间线索、功能影响程度
            - **symptomTags**：症状标签集合（逗号分隔），从受控词汇表中选取，按相关性降序排列

            ## 评估推理规则
            1. 优先依据"标准症状列表"中的症状数量和严重程度判断状态等级
            2. 结合"情绪统计"中的主导情绪和趋势方向进行交叉验证
            3. 参考"症状知识参考"和"诊断标准参考"校准评估准确性
            4. 若存在"历史诊断摘要"，需考虑症状的持续性和变化趋势
            5. 当症状跨多个类别（如同时存在焦虑和抑郁）时，优先判断为共病状态
            6. 若输入数据不足以做出可靠判断，psychologicalState选择"其他"

            ## 注意事项
            - symptomSummary应精练概括，避免简单罗列标签，需体现症状间的关联和整体图景
            - symptomTags中每个标签必须是受控词汇表中的标准术语，不可自行创造或使用修饰语
            - 当用户症状较轻或数据不足时，宁可保守评估，不可过度诊断
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
        return "psychologicalState";
    }

    @Override
    protected String getAgentDescription() {
        return "心理状态与症状评估";
    }

    @Override
    protected Class<?> getOutputType() {
        return PsychologicalStateResult.class;
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
        double topP = config != null ? config.getTopP().doubleValue() : 0.9;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.6;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.2;

        return ChatOptions.builder()
                .topK(40)
                .topP(topP)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .temperature(temperature)
                .maxTokens(maxToken)
                .build();
    }

    @Retryable(
            label = "psychological-state-process-model",
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

    @Retryable(
            label = "psychological-state-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public PsychologicalStateResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config, runnableConfig);
    }

    public PsychologicalStateResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return callForResult(chatModel, userPrompt, config, null);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return stream(chatModel, userPrompt, config, null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PsychologicalStateResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String psychologicalState;

        private String symptomSummary;

        private String symptomTags;
    }
}