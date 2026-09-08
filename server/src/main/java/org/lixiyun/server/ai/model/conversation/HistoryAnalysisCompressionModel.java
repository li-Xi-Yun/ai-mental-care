package org.lixiyun.server.ai.model.conversation;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
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
 * @since 2026-07-15 15:32
 */
@Slf4j
@Component
public class HistoryAnalysisCompressionModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    /**
     * 系统提示词：定义AI的角色和行为准则（历史情绪分析压缩专家）
     */
    private final String defaultSystemPrompt = """
            Role: 历史情绪分析压缩专家
            Profile:
              description: 你是一个专注于心理健康对话的情绪分析摘要引擎，擅长从多条情绪分析记录中提取核心情绪变化趋势和关键心理特征。
            Goals:
              1. 将多条历史情绪分析结果压缩为一段连贯、准确、无冗余的中文摘要。
              2. 保留关键情绪指标（PAD三维情绪值、正负向情绪占比变化趋势）。
              3. 突出情绪转折点和显著心理特征。
              4. 仅输出摘要文本本身，不添加标题、解释或额外内容。
            Constraints:
              1. 不得虚构或推测原文未提及的情绪数据。
              2. 保持客观专业的语气，避免主观臆断。
              3. 摘要长度控制在2000字，确保信息密度最大化。
              4. 使用结构化表达：按时间顺序描述情绪演变过程。
              5. 若输入为空或无有效内容，返回空字符串。
            Skills:
              1. 识别情绪分析中的关键数值变化（如P/A/D分数波动）。
              2. 融合多轮分析结果，消除冗余，突出趋势。
              3. 在有限篇幅内准确概括用户的心理状态演变轨迹。
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
        return "historyAnalysisCompression";
    }

    @Override
    protected String getAgentDescription() {
        return "专业历史情绪分析数据压缩助手";
    }

    @Override
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getOllamaModelName() != null ? config.getOllamaModelName() : defaultOllamaModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.15;
        double topP = config != null ? config.getTopP().doubleValue() : 0.82;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 20;
        Double repeatPenalty = config != null && config.getRepeatPenalty() != null ? config.getRepeatPenalty().doubleValue() : 1.25;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;
        Integer seed = config != null ? config.getSeed() : 42;

        return OllamaChatOptions.builder()
                .model(modelName)
                .keepAlive("30m")
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .numPredict(maxToken)
                .seed(seed)
                .repeatPenalty(repeatPenalty)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .repeatLastN(30)
                .penalizeNewline(true)
                .stop(List.of("\n\n\n", "```", "## ", "### ", "【", "】"))
                .truncate(true)
                .format(null)
                .numCtx(8192)
                .numThread(Math.max(2, Runtime.getRuntime().availableProcessors() / 2))
                .numBatch(1024)
                .mirostat(2)
                .mirostatTau(2.5f)
                .mirostatEta(0.1f)
                .useMMap(true)
                .useMLock(false)
                .numGPU(-1)
                .lowVRAM(false)
                .build();
    }

    @Override
    protected ChatOptions buildDashScopeCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDashscopeModelName() != null ? config.getDashscopeModelName() : defaultDashscopeModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.15;
        double topP = config != null ? config.getTopP().doubleValue() : 0.82;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 20;
        Integer seed = config != null ? config.getSeed() : 42;

        return DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .seed(seed)
                .maxToken(maxToken)
                .repetitionPenalty(1.25)
                .stop(List.of("\n\n\n", "```", "【", "】"))
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(DashScopeResponseFormat.Type.TEXT)
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
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.15;
        double topP = config != null ? config.getTopP().doubleValue() : 0.82;
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
                        .type(ResponseFormat.Type.TEXT)
                        .build())
                .stop(List.of("\n\n\n", "```", "【", "】", "1.", "2.", "3."))
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
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.15;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return ChatOptions.builder()
                .topK(20)
                .topP(0.82)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
                .temperature(temperature)
                .maxTokens(maxToken)
                .build();
    }

    @Retryable(
            label = "history-analysis-compression-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config);
    }

}