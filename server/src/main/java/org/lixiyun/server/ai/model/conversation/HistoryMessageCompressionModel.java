package org.lixiyun.server.ai.model.conversation;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
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

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-07-15 15:31
 */
@Slf4j
@Component
public class HistoryMessageCompressionModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    /**
     * 系统提示词：定义AI的角色和行为准则（对话语义压缩专家）
     */
    private final String defaultSystemPrompt = """
            Role: 对话语义压缩专家
            Profile:
              description: 你是一个专注于心理健康对话的语义压缩引擎，擅长从多轮心理陪伴对话中提取核心信息并生成高度凝练的第三人称摘要。
            InputFormat:
              - 输入由"角色：内容"格式的对话记录组成，角色包括用户和AI心理陪伴助手。
              - 若存在【历史消息压缩上下文】，则为前序压缩摘要，需与新对话融合后重新压缩为统一摘要。
            Goals:
              1. 将用户与AI心理陪伴助手之间的完整对话历史（含情感交流、建议互动）压缩为一段连贯、准确、无冗余的中文摘要。
              2. 保留关键情感变化节点和重要建议内容。
              3. 突出用户的情绪状态演变和关注焦点。
              4. 仅输出摘要文本本身，不添加标题、解释或额外内容。
            Constraints:
              1. 不得虚构或推测原文未提及的内容。
              2. 保持客观中立的语气，准确反映对话实质。
              3. 摘要长度控制在2000字以内，确保信息密度最大化。
              4. 按时间顺序描述对话演进过程，不同阶段之间用中文分号或句号分隔。
              5. 若输入为空或无有效内容，返回空字符串。
              6. 不得保留任何 ReAct 格式的痕迹（如 Thought/Action/Observation 标签）。
              7. 禁止使用 Markdown 语法、表情符号、emoji；禁止使用繁体中文。
              8. 若存在历史压缩上下文，将其视为已压缩的早期对话，与新消息统一按时间线融合压缩，而非简单拼接。
            OutputFormat:
              以纯文本连续段落输出，示例：
              用户因[事件]表达[情绪]，AI通过[策略]进行回应；随后用户[状态变化]，AI建议[内容]；最终用户[结果状态]。
            Skills:
              1. 识别对话中的情感转折点和关键咨询节点。
              2. 融合多轮交互信息与已有压缩摘要，消除重复，保持时序逻辑清晰。
              3. 在有限篇幅内准确概括用户的心理状态变化轨迹和AI提供的核心建议。
            """;

    private final int defaultMaxToken = 2000;

    @Override
    protected String getSystemPrompt(AiNodeConfig config) {
        if (config != null && config.getSystemPrompt() != null) {
            return config.getSystemPrompt();
        }
        return defaultSystemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "historyMessageCompression";
    }

    @Override
    protected String getAgentDescription() {
        return "专业对话语义压缩助手";
    }

    @Override
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getOllamaModelName() != null ? config.getOllamaModelName() : defaultOllamaModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.12;
        double topP = config != null ? config.getTopP().doubleValue() : 0.80;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 18;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.75;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.35;
        List<String> stopSequences = resolveStopSequences(config, List.of("\n\n\n", "```", "## ", "### ", "【", "】"));

        return OllamaChatOptions.builder()
                .model(modelName)
                .keepAlive("30m")
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .numPredict(maxToken)
                .seed(42)
                .repeatPenalty(1.28)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .repeatLastN(35)
                .penalizeNewline(true)
                .stop(stopSequences)
                .truncate(true)
                .format(isJsonResponseFormat(config) ? "json" : null)
                .numCtx(8192)
                .numThread(Math.max(2, Runtime.getRuntime().availableProcessors() / 2))
                .numBatch(1024)
                .mirostat(2)
                .mirostatTau(2.3f)
                .mirostatEta(0.12f)
                .useMMap(true)
                .useMLock(false)
                .numGPU(-1)
                .lowVRAM(false)
                .build();
    }

    @Override
    protected ChatOptions buildDashScopeCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDashscopeModelName() != null ? config.getDashscopeModelName() : defaultDashscopeModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.12;
        double topP = config != null ? config.getTopP().doubleValue() : 0.80;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 18;
        List<Object> stopSequences = resolveDashScopeStopSequences(config, List.of("\n\n\n", "```", "【", "】"));

        return DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .seed(42)
                .maxToken(maxToken)
                .repetitionPenalty(1.28)
                .stop(stopSequences)
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(isJsonResponseFormat(config) ? DashScopeResponseFormat.Type.JSON_OBJECT : DashScopeResponseFormat.Type.TEXT)
                        .build())
                .enableThinking(false)
                .enableSearch(false)
                .stream(false)
                .incrementalOutput(false)
                .multiModel(false)
                .vlHighResolutionImages(false)
                .tools(null)
                .toolChoice("none")
                .internalToolExecutionEnabled(false)
                .toolContext(java.util.Map.of())
                .build();
    }

    @Override
    protected ChatOptions buildDeepSeekCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDeepseekModelName() != null ? config.getDeepseekModelName() : defaultDeepseekModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.12;
        double topP = config != null ? config.getTopP().doubleValue() : 0.80;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.75;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.35;
        List<String> stopSequences = resolveStopSequences(config, List.of("\n\n\n", "```", "【", "】", "1.", "2.", "3."));

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
                .stop(stopSequences)
                .logprobs(false)
                .topLogprobs(null)
                .tools(null)
                .toolChoice("none")
                .internalToolExecutionEnabled(false)
                .toolContext(java.util.Map.of())
                .toolCallbacks(java.util.List.of())
                .build();
    }

    @Override
    protected ChatOptions buildDefaultCompanionOptions(AiNodeConfig config) {
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.12;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return ChatOptions.builder()
                .topK(18)
                .topP(0.80)
                .frequencyPenalty(0.75)
                .presencePenalty(0.35)
                .temperature(temperature)
                .maxTokens(maxToken)
                .build();
    }

    @Retryable(
            label = "history-message-compression-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return call(chatModel, userPrompt, config, null);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return stream(chatModel, userPrompt, config, null);
    }
}