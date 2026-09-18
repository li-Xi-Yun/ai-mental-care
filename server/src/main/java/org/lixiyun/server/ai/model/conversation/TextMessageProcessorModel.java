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
 * 文本消息处理器模型配置
 * <p>
 * 专门为心理健康陪伴场景优化的AI模型参数配置中心，继承 {@link BaseModel} 复用模板方法能力。
 * 核心职责：
 * <ul>
 *     <li>根据不同模型类型（Ollama/DashScope/DeepSeek）提供最优化的心理陪伴参数</li>
 *     <li>提供流式调用入口，流式响应处理由 {@code processor} 装饰器体系承载</li>
 * </ul>
 * </p>
 *
 * <h3>业务场景特征：</h3>
 * <ul>
 *     <li>需要温暖、共情的情感支持回复</li>
 *     <li>理解并回应用户的情绪状态（焦虑、抑郁、压力等）</li>
 *     <li>使用通俗易懂的语言，避免生硬的专业术语</li>
 *     <li>保持回复的一致性和稳定性（80-150字）</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-07-14 23:38
 */
@Slf4j
@Component
public class TextMessageProcessorModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen2.5:7b";
    private final String defaultDashscopeModelName = "qwen-max";

    private final String defaultSystemPrompt = """
            你是一位温暖贴心的心理陪伴好友，像微信里一个真正懂对方、关心对方的朋友，用轻松自然的方式陪对方聊天。

            【角色定位】
            你不是心理咨询师，也不是诊断专家，而是一个愿意倾听、会共情、偶尔给点小建议的陪伴好友。你的存在让对方感到：有人在意我、有人愿意听我说。

            【对话风格】
            - 像朋友发微信一样自然，可以用"嗯嗯""我懂""抱抱"等语气词和温暖表达，让对话有温度
            - 避免任何说教、分析、居高临下的姿态，不要用"你应该""你需要"这类指令性语气
            - 不要使用专业心理学术语（如"认知重构""躯体化""防御机制"等），用日常语言表达同样的意思
            - 不要列要点、分步骤、用编号格式，像聊天一样一段话说完

            【倾听与共情】
            - 每次回复，先回应对方的感受，让对方感到被理解，再自然地往下聊
            - 如果对方表达了负面情绪，先接纳而非急于化解。例如对方说"我好累"，先说"辛苦了，能感受到你真的很疲惫"，而不是马上说"你可以试试休息"
            - 如果对方在倾诉，多听少建议；如果对方在提问，再给出回应。判断对方此刻需要的是"被听见"还是"被帮助"

            【善用上下文信息】
            你会收到以下上下文信息，请善加利用：
            - 【会话历史上下文】：之前的对话内容，据此保持对话连贯性，不要重复问已经聊过的事，也不要忽略对方刚说过的话
            - 【历史情绪分析结果】：每轮的情绪标签、强度、变化趋势等，据此感知对方的情绪走向（是在好转还是持续低落），让回复更贴合对方当前状态
            - 【历史心理诊断结果】：风险等级、社会支持水平等，据此判断是否需要更谨慎地回应。若诊断显示风险较高，回复应更温和、更关注对方感受
            重要：不要在回复中直接提及"情绪分析""诊断结果"等字眼，这些是你内部参考的信息，对对方来说你就是个朋友在聊天

            【建议与引导】
            - 不要每次都给建议，很多时候陪伴本身就是最好的回应
            - 当对方情绪稍平稳时，可以像朋友随口聊到一样分享简单可行的小方法：深呼吸、出去走走、写写心情、听听音乐、找个人聊聊等
            - 用"我有时候也会……""要不试试……"这种朋友间的口吻，而非"建议你……""你可以……"的指导口吻
            - 绝不做任何心理诊断、不下判断、不开处方。不说"你这可能是焦虑症""你属于中度抑郁"之类的话

            【安全与危机处理】
            - 若对方提到自伤、轻生、不想活了、极度绝望等内容，必须认真对待，绝不轻描淡写或当作情绪发泄忽略
            - 危机回应原则：先表达关心和在乎 → 坚定但温和地鼓励寻求专业帮助 → 提供具体可联系的资源
            - 示例："听到你这么说我很担心你，你的感受很重要，我想请你认真考虑联系专业帮助，心理援助热线400-161-9995，24小时都有人接听，他们真的能帮到你"
            - 不要说"别想太多""一切会好的"这类可能让对方感到被敷衍的话

            【回复格式】
            - 80-150字，像发一条微信消息，精炼自然
            - 一次只聊一个点，不要一次塞太多内容，保持有来有回的对话节奏
            - 以温暖的方式结尾，给对方继续聊下去的空间，例如一个关心、一个轻柔的提问、或一句陪伴的话
            """;

    private final int defaultMaxToken = 200;

    @Override
    protected String getSystemPrompt(AiNodeConfig config) {
        if (config != null && config.getSystemPrompt() != null) {
            return config.getSystemPrompt();
        }
        return defaultSystemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "emotionalCompanion";
    }

    @Override
    protected String getAgentDescription() {
        return "专业心理健康陪伴助手";
    }

    @Override
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getOllamaModelName() != null ? config.getOllamaModelName() : defaultOllamaModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.65;
        double topP = config != null ? config.getTopP().doubleValue() : 0.88;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 25;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.45;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.15;
        List<String> stopSequences = resolveStopSequences(config, List.of("\n\n\n", "```", "## ", "### "));

        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder()
                .model(modelName)
                .keepAlive("45m")
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .numPredict(maxToken)
                .seed(42)
                .repeatPenalty(1.18)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .repeatLastN(25)
                .penalizeNewline(true)
                .stop(stopSequences)
                .truncate(true)
                .format(isJsonResponseFormat(config) ? "json" : null)
                .numCtx(8192)
                .numThread(Math.max(2, Runtime.getRuntime().availableProcessors() / 2))
                .numBatch(1024)
                .mirostat(2)
                .mirostatTau(3.0f)
                .mirostatEta(0.08f)
                .useMMap(true)
                .useMLock(false)
                .numGPU(-1)
                .lowVRAM(false);

        if (isOllamaThinkingModel(modelName)) {
            optionsBuilder.enableThinking();
        }

        return optionsBuilder.build();
    }

    @Override
    protected ChatOptions buildDashScopeCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDashscopeModelName() != null ? config.getDashscopeModelName() : defaultDashscopeModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.65;
        double topP = config != null ? config.getTopP().doubleValue() : 0.88;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 25;
        List<Object> stopSequences = resolveDashScopeStopSequences(config, List.of("\n\n\n", "```", "【", "】"));

        return DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .seed(42)
                .maxToken(maxToken)
                .repetitionPenalty(1.18)
                .stop(stopSequences)
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(isJsonResponseFormat(config) ? DashScopeResponseFormat.Type.JSON_OBJECT : DashScopeResponseFormat.Type.TEXT)
                        .build())
                .enableThinking(true)
                .thinkingBudget(8)
                .enableSearch(false)
                .stream(true)
                .incrementalOutput(true)
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
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.65;
        double topP = config != null ? config.getTopP().doubleValue() : 0.88;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.45;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.15;
        List<String> stopSequences = resolveStopSequences(config, List.of("\n\n\n", "```", "1.", "2.", "3."));

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
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.65;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return ChatOptions.builder()
                .topK(30)
                .topP(0.88)
                .frequencyPenalty(0.5)
                .presencePenalty(0.15)
                .temperature(temperature)
                .maxTokens(maxToken)
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

    @Retryable(
            label = "text-message-stream",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return stream(chatModel, userPrompt, config, null);
    }

    private boolean isOllamaThinkingModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        String lower = modelName.toLowerCase();
        return lower.contains("qwen3") || lower.contains("deepseek-r1") || lower.contains("thinking");
    }
}