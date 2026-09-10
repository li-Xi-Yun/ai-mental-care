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
              description: 你是一个专注于心理健康对话的情绪分析摘要引擎，擅长从多条情绪分析记录中提取核心情绪变化趋势和关键心理特征，生成增量可融合的结构化摘要。
            InputSchema:
              每条情绪分析记录包含以下字段：
              - roundNum: 对话轮次序号，是建立时序的关键
              - emotionLabel: 主情绪标签（如anger/开心/neutral/不满）
              - emotionSubLabel: 情绪细分标签（如不满/暴怒/抱怨）
              - emotionConfidence: 情绪识别置信度（0~1），值越高该条分析越可靠
              - emotionIntensity: 情绪强烈程度（0~1）
              - emotionTrend: 较上一轮的情绪变化趋势，是识别转折点的核心信号
              - pScore/aScore/dScore: PAD三维情绪值（愉悦度/唤醒度/支配度，各-1~1）
              - positiveEmotionRatio: 正向情绪占比（0~1）
              - negativeEmotionRatio: 负向情绪占比（0~1）
              - neutralEmotionRatio: 中性情绪占比（0~1）
              - analysisContent: 情绪分析详情文本
            Goals:
              1. 将多条历史情绪分析结果压缩为一段连贯、准确、无冗余的中文摘要。
              2. 保留关键情绪指标：PAD三维情绪值的变化轨迹、正向/负向/中性情绪占比的演变趋势。
              3. 突出情绪转折点（emotionTrend发生显著变化的轮次）和显著心理特征。
              4. 若存在【历史情绪分析压缩上下文】，将其作为前序摘要与新分析数据融合，消除重叠、延续趋势。
              5. 仅输出摘要文本本身，不添加标题、解释或额外内容。
            OutputFormat:
              按轮次时间线组织摘要，示例结构：
              第1~3轮：用户情绪以[主标签]为主，P值从x升至y，正向占比从a%降至b%……
              第4轮出现转折：emotionTrend显示[变化方向]，情绪切换为[新标签]，A值骤升至z……
              第5~8轮：情绪逐步[趋势描述]……
            Constraints:
              1. 不得虚构或推测原文未提及的情绪数据。
              2. 保持客观专业的语气，避免主观臆断。
              3. 摘要长度控制在2000字，确保信息密度最大化。
              4. 严格按roundNum升序组织，形成清晰的轮次时间线。
              5. 优先保留emotionConfidence≥0.7的高置信度分析结论，低置信度结论标注"待确认"。
              6. 若输入为空或无有效内容，返回空字符串。
              7. 禁止使用Markdown格式、表情符号、换行符或非简体中文字符。
              8. 不得保留任何ReAct格式的痕迹（如Thought/Action/Observation标签）。
            Skills:
              1. 识别emotionTrend中的转折信号，定位情绪突变轮次。
              2. 追踪PAD三维分数（P/A/D）的波动趋势，量化情绪演变方向。
              3. 对比正向/负向/中性情绪占比的轮间变化，提炼占比迁移规律。
              4. 融合多轮分析结果与已有压缩上下文，消除冗余，保持时序逻辑连贯。
              5. 在有限篇幅内准确概括用户的心理状态演变轨迹。
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
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;
        List<String> stopSequences = resolveStopSequences(config, List.of("\n\n\n", "```", "## ", "### ", "【", "】"));

        return OllamaChatOptions.builder()
                .model(modelName)
                .keepAlive("30m")
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .numPredict(maxToken)
                .seed(42)
                .repeatPenalty(1.25)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .repeatLastN(30)
                .penalizeNewline(true)
                .stop(stopSequences)
                .truncate(true)
                .format(isJsonResponseFormat(config) ? "json" : null)
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
        List<Object> stopSequences = resolveDashScopeStopSequences(config, List.of("\n\n\n", "```", "【", "】"));

        return DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .seed(42)
                .maxToken(maxToken)
                .repetitionPenalty(1.25)
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
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.15;
        double topP = config != null ? config.getTopP().doubleValue() : 0.82;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;
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