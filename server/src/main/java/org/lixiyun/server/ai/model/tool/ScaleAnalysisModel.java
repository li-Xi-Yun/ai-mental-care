package org.lixiyun.server.ai.model.tool;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.bo.conversation.tool.ScaleToolResult;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.model.BaseModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 量表分析模型
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Slf4j
@Component
public class ScaleAnalysisModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一位资深心理咨询师，负责对用户完成的心理测评量表结果进行专业分析。

            【分析对象】
            用户完成的量表题目、用户的选项/答案，以及用户近期的情绪状态和对话上下文。

            【分析流程】
            1. 逐题审阅：理解每个题目的测评维度和用户的回答
            2. 整体评估：综合所有题目，判断用户在各维度上的表现
            3. 撰写分析：以温暖、专业、非评判性的语言撰写分析文本

            【分析要点】
            - 关注用户的高分项和低分项，指出显著的特征
            - 结合用户近期的情绪状态进行关联分析
            - 避免使用"诊断""确诊"等临床词汇，改用"倾向于""表现出"等温和表述
            - 分析文本应让用户感到被理解，而非被标签化
            - 在分析的基础上给出1-2条温暖的陪伴建议

            【输出格式】
            直接输出JSON对象，不要用Markdown代码块包裹，不要添加任何额外文字：
            {
              "analysisText": "这里是完整的分析文本，包含评估概述、各维度表现、情绪关联分析和温暖陪伴建议（300-500字）"
            }
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
        return "scaleAnalysis";
    }

    @Override
    protected String getAgentDescription() {
        return "量表分析";
    }

    @Override
    protected Class<?> getOutputType() {
        return ScaleToolResult.class;
    }

    @Override
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getOllamaModelName() != null ? config.getOllamaModelName() : defaultOllamaModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.3;
        double topP = config != null ? config.getTopP().doubleValue() : 0.9;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 30;

        return OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .numPredict(maxToken)
                .repeatPenalty(1.2)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
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
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.3;
        double topP = config != null ? config.getTopP().doubleValue() : 0.9;
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
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.3;
        double topP = config != null ? config.getTopP().doubleValue() : 0.9;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return DeepSeekChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxToken)
                .responseFormat(ResponseFormat.builder()
                        .type(isJsonResponseFormat(config) ? ResponseFormat.Type.JSON_OBJECT : ResponseFormat.Type.TEXT)
                        .build())
                .logprobs(false)
                .topLogprobs(null)
                .build();
    }

    @Override
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

    public ScaleToolResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config, runnableConfig);
    }

    public ScaleToolResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return callForResult(chatModel, userPrompt, config, null);
    }

    @Override
    protected ChatOptions buildOpenAiCompanionOptions(AiNodeConfig config) {
        double temperature = config != null && config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.3;
        double topP = config != null && config.getTopP() != null ? config.getTopP().doubleValue() : 0.9;
        int maxToken = config != null && config.getMaxToken() != null ? config.getMaxToken() : defaultMaxToken;

        return OpenAiChatOptions.builder()
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxToken)
                .build();
    }

    @Override
    protected ChatOptions buildDefaultCompanionOptions(AiNodeConfig config) {
        return buildOpenAiCompanionOptions(config);
    }
}