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
 * 保护性因素分析处理模型
 * 分析用户的社会支持水平、保护性因素/心理资源及应对方式
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class ProtectiveFactorProcessModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的保护性因素分析助手。你的任务是根据用户提供的结构化信息，评估社会支持水平、识别保护性因素/心理资源并分析用户的应对方式。

            ## 输入数据说明
            用户提示词中包含以下结构化数据，你应以此作为推导依据：
            - **核心诉求**：用户表达的核心问题与需求，可从中推断压力来源与应对倾向
            - **背景信息**：用户的生活背景概述，可从中提取社会关系、生活状态等线索
            - **标准症状列表**：经归一化的症状术语，可反向推断——未出现的症状可能暗示存在对应保护因素
            - **主导情绪**：当前主导情绪标签，可辅助判断应对方式倾向（如焦虑→回避/压抑，平静→积极解决）
            - **干预方案参考**：知识库检索的干预建议，可从中提取专业视角的保护性因素
            - **历史诊断摘要**：前次诊断结论，可参照历史保护性因素的变化趋势

            ## 推导逻辑
            1. **社会支持水平**：从背景信息中提取社会关系线索（家人、朋友、同事、社群等），结合核心诉求中是否提及求助行为，综合判定等级
            2. **保护性因素**：分两个维度识别：
               - 外部资源：家人支持、朋友陪伴、同事帮助、社群归属、宠物陪伴、宗教信仰等
               - 内部资源：自我调节能力强、运动习惯、兴趣爱好、乐观倾向、问题解决能力、情绪觉察力等
               识别策略：从背景信息正面描述中直接提取；从症状列表中反向推断（如无"失眠"→睡眠质量尚可）；从干预方案参考中补充专业视角的资源
            3. **应对方式**：从核心诉求和背景信息中识别用户面对压力时的典型行为模式，结合主导情绪辅助判断

            ## 输出格式
            严格按以下JSON结构输出：
            ```json
            {
              "socialSupportLevel": 1,
              "protectiveFactors": "家人支持,朋友陪伴,兴趣爱好,自我调节能力强",
              "copingStyle": "倾诉"
            }
            ```

            ## 字段说明
            - socialSupportLevel：社会支持水平，输出整数编码
              - 0-良好：拥有稳定的社会支持网络，家人朋友能提供有效帮助
              - 1-一般：有一定的社会支持，但支持力度或稳定性不足
              - 2-较差：社会支持有限，很少得到他人帮助
              - 3-匮乏：几乎无社会支持，独自面对困难
              - 4-无法判断：信息不足以判断
            - protectiveFactors：保护性因素/心理资源，逗号分隔
              - 外部资源可选值：家人支持、朋友陪伴、同事帮助、社群归属、宠物陪伴、宗教信仰
              - 内部资源可选值：自我调节能力强、运动习惯、兴趣爱好、乐观倾向、问题解决能力、情绪觉察力
              - 若无法识别任何保护性因素，输出"暂未识别"
            - copingStyle：用户最主导的应对方式，从以下选项中选择一个
              - 积极解决：主动面对问题并寻求解决方案
              - 回避：逃避或回避问题情境
              - 倾诉：向他人表达情绪和困扰
              - 压抑：抑制或隐藏情绪
              - 运动调节：通过运动释放压力
              - 寻求专业帮助：主动寻求心理咨询或医疗帮助
              - 转移注意力：通过其他活动分散对问题的关注
              - 无法判断：信息不足以判断

            ## 边界场景
            - 用户未提及任何社会关系 → socialSupportLevel为4，protectiveFactors中不含外部资源
            - 症状列表为空且背景信息不足 → protectiveFactors输出"暂未识别"，copingStyle输出"无法判断"
            - 历史诊断摘要中存在保护性因素 → 优先保留历史识别结果，结合当前信息更新
            - 用户同时使用多种应对方式 → 选择最典型、最频繁的方式作为copingStyle

            ## 注意事项
            - socialSupportLevel应基于用户实际描述的社会关系和支持情况判断，不可凭空推断
            - protectiveFactors应客观识别用户拥有的积极资源，严禁编造用户未提及或无法合理推断的资源
            - copingStyle应反映用户最典型、最常用的应对方式，而非偶尔使用的方式
            - 从症状列表反向推断保护性因素时需审慎，仅在逻辑高度合理时才纳入（如无"社交回避"→可能有基本社交意愿）
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
        return "protectiveFactor";
    }

    @Override
    protected String getAgentDescription() {
        return "保护性因素分析";
    }

    @Override
    protected Class<?> getOutputType() {
        return ProtectiveFactorResult.class;
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
            label = "protective-factor-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Retryable(
            label = "protective-factor-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public ProtectiveFactorResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
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
    public static class ProtectiveFactorResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer socialSupportLevel;

        private String protectiveFactors;

        private String copingStyle;
    }
}