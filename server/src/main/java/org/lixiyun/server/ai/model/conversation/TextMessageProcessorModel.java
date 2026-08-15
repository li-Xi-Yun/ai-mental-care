package org.lixiyun.server.ai.model.conversation;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
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

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final String systemPrompt = "你是一位温暖专业的心理陪伴师。你的任务是：\n" +
            "1. 用共情的方式理解用户的情绪状态\n" +
            "2. 提供温暖、专业的情感支持建议\n" +
            "3. 使用简洁通俗的语言（避免专业术语）\n" +
            "4. 回复控制在80-150字，保持亲切自然\n" +
            "5. 如遇严重心理问题，建议寻求专业心理咨询师帮助";

    private static final int maxToken = 200;

    @Override
    protected String getSystemPrompt() {
        return systemPrompt;
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
    protected ChatOptions buildOllamaCompanionOptions() {
        return OllamaChatOptions.builder()

                .model(ollamaModelName)
                .keepAlive("45m")

                .temperature(0.65)
                .topK(25)
                .topP(0.88)
                .numPredict(maxToken)
                .seed(42)

                .repeatPenalty(1.18)
                .frequencyPenalty(0.45)
                .presencePenalty(0.15)
                .repeatLastN(25)
                .penalizeNewline(true)

                .stop(List.of("\n\n\n", "```", "## ", "### "))
                .truncate(true)
                .format(null)

                .numCtx(8192)
                .numThread(Math.max(2, Runtime.getRuntime().availableProcessors() / 2))
                .numBatch(1024)

                .enableThinking()
                .mirostat(2)
                .mirostatTau(3.0f)
                .mirostatEta(0.08f)

                .useMMap(true)
                .useMLock(false)
                .numGPU(-1)
                .lowVRAM(false)

                .build();
    }

    @Override
    protected ChatOptions buildDashScopeCompanionOptions() {
        return DashScopeChatOptions.builder()

                .model(dashscopeModelName)
                .temperature(0.65)
                .topP(0.88)
                .topK(25)
                .seed(42)
                .maxToken(maxToken)

                .repetitionPenalty(1.18)

                .stop(List.of("\n\n\n", "```", "【", "】"))
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(DashScopeResponseFormat.Type.TEXT)
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
    protected ChatOptions buildDeepSeekCompanionOptions() {
        return DeepSeekChatOptions.builder()

                .model(deepseekModelName)
                .temperature(0.65)
                .topP(0.88)
                .maxTokens(maxToken)

                .frequencyPenalty(0.45)
                .presencePenalty(0.15)

                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.TEXT)
                        .build())

                .stop(List.of("\n\n\n", "```", "1.", "2.", "3."))

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
    protected ChatOptions buildDefaultCompanionOptions() {
        return ChatOptions.builder()
                .topK(30)
                .topP(0.88)
                .frequencyPenalty(0.5)
                .presencePenalty(0.15)
                .temperature(0.65)
                .maxTokens(maxToken)
                .build();
    }

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCall(chatModel, userPrompt);
    }

    @Retryable(
            label = "text-message-stream",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doStream(chatModel, userPrompt);
    }
}