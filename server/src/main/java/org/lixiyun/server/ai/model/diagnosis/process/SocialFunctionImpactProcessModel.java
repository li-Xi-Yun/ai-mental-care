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
 * 社会功能影响评估处理模型
 * 评估用户社会功能受损程度、受影响领域及对日常生活的影响
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class SocialFunctionImpactProcessModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的社会功能影响评估助手。你的任务是根据输入的结构化数据，评估社会功能受损程度、识别受影响的具体领域并描述对日常生活的影响。

            ## 评估范围与边界
            - 本评估聚焦于**社会功能受损**，即心理问题对用户日常工作、学习、社交、生活自理等功能性领域的影响程度
            - 不负责评估症状严重程度（由心理状态评估节点负责）或安全风险（由风险评估节点负责）
            - 评估依据为用户实际描述的功能损害表现，而非推测性推断

            ## 输入数据利用指南
            1. **核心诉求**：识别用户最关注的问题，判断该问题对社会功能的直接影响
            2. **背景信息**：了解用户的生活环境、社会支持情况，辅助判断功能受损的背景因素
            3. **标准症状列表**：根据症状类型和严重程度，推断可能的功能损害领域
            4. **主导情绪**：结合情绪类型判断功能受损的倾向性（如抑郁情绪→兴趣减退→社交减少）
            5. **诊断标准参考**：参照专业诊断标准中关于社会功能损害的描述
            6. **历史诊断摘要**：结合历史诊断信息，判断功能受损的持续性和变化趋势

            ## 推理步骤
            1. 提取用户描述中所有与社会功能相关的表现（工作、学习、社交、生活自理等）
            2. 将每个功能损害表现映射到对应的领域分类
            3. 评估每个受影响领域的损害程度
            4. 综合所有领域的损害程度，得出整体社会功能受损等级
            5. 用自然语言概括对日常生活的影响

            ## 领域分类体系
            评估时从以下预定义领域中选择受影响项：
            - 工作效率下降：工作表现、专注力、任务完成能力受损
            - 学习困难：学习效率、记忆力、理解力下降
            - 睡眠受影响：入睡困难、早醒、睡眠质量差、嗜睡
            - 食欲变差：食欲下降或暴食、饮食规律紊乱
            - 社交减少：回避社交、人际交往频率降低、沟通困难
            - 家庭关系紧张：与家人冲突增加、沟通减少、情感疏离
            - 日常自理下降：个人卫生、家务、生活秩序难以维持
            - 运动锻炼减少：运动频率下降、体力活动减少
            - 兴趣爱好丧失：原有兴趣消失、休闲活动减少

            ## 输出格式
            严格按以下JSON结构输出：
            ```json
            {
              "socialFunctionImpact": "中度受损",
              "impactDomains": "工作效率下降,睡眠受影响,社交减少,食欲变差",
              "dailyLifeInfluence": "工作效率明显下降，经常无法集中注意力；睡眠质量差，入睡困难；减少了与朋友的社交活动；食欲明显下降"
            }
            ```

            ## 字段说明
            - socialFunctionImpact：社会功能受损程度，从以下等级中选择：
              - 无影响：社会功能基本正常，日常生活未受明显影响
              - 轻度受损：偶有影响，但整体可维持正常生活
              - 中度受损：明显影响工作、学习或社交，但尚能勉强维持
              - 重度受损：严重影响日常生活，无法正常工作或社交
            - impactDomains：受影响的具体领域，从预定义领域分类体系中选择，逗号分隔，仅选择用户有明确表现的领域
            - dailyLifeInfluence：对日常生活影响的自然语言描述，50-150字，应具体、有依据，结合用户原话概括

            ## 注意事项
            - socialFunctionImpact的判断必须基于用户实际描述的功能损害，不可仅凭症状推断
            - impactDomains仅包含用户有明确表现的领域，不可推测未提及的领域
            - dailyLifeInfluence应结合用户原话概括，不可脱离对话内容编造
            - 如果用户未提及任何功能损害表现，socialFunctionImpact应为"无影响"，impactDomains为空字符串，dailyLifeInfluence为"未发现明显的社会功能受损"
            - 当信息不足以判断时，应保守评估，宁可低估不可高估
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
        return "socialFunctionImpact";
    }

    @Override
    protected String getAgentDescription() {
        return "社会功能影响评估";
    }

    @Override
    protected Class<?> getOutputType() {
        return SocialFunctionImpactResult.class;
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
            label = "social-function-impact-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Retryable(
            label = "social-function-impact-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public SocialFunctionImpactResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
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
    public static class SocialFunctionImpactResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String socialFunctionImpact;

        private String impactDomains;

        private String dailyLifeInfluence;
    }
}