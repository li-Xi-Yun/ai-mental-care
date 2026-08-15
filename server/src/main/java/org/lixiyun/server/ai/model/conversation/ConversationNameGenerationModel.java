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
 * 会话名称生成模型
 * <p>根据用户首次对话内容，调用大模型生成简洁的会话名称</p>
 *
 * @author lixiyun
 * @since 2026-08-14
 */
@Slf4j
@Component
public class ConversationNameGenerationModel extends BaseModel {

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private static final int maxToken = 128;

    private final String systemPrompt = """
            Role: 会话命名专家
            Profile:
              description: 你是一个专注于心理健康对话的命名引擎，擅长从用户的首条消息中提炼核心主题，生成简洁、贴切的会话名称。
            Goals:
              1. 根据用户发送的消息内容，生成一个简短且能概括对话主题的会话名称。
              2. 名称应体现用户的核心关注点或情绪状态。
            Constraints:
              1. 名称长度不超过15个字。
              2. 不得虚构或推测用户未提及的内容。
              3. 仅输出名称文本本身，不添加引号、标题、解释或任何额外内容。
              4. 禁止使用Markdown格式、表情符号或非简体中文字符。
              5. 若输入为空或无有效内容，返回"新对话"。
            Skills:
              1. 识别用户消息中的核心情感和关注焦点。
              2. 用精炼的语言概括对话主题。
            """;

    @Override
    protected String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "conversationNameGeneration";
    }

    @Override
    protected String getAgentDescription() {
        return "会话名称生成";
    }

    @Override
    protected ChatOptions buildOllamaCompanionOptions() {
        return OllamaChatOptions.builder()
                .model(ollamaModelName)
                .keepAlive("30m")
                .temperature(0.3)
                .topK(20)
                .topP(0.85)
                .numPredict(maxToken)
                .seed(42)
                .repeatPenalty(1.2)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
                .repeatLastN(50)
                .penalizeNewline(true)
                .stop(List.of("\n\n\n", "```", "## ", "### ", "【", "】"))
                .truncate(true)
                .format(null)
                .numCtx(2048)
                .numThread(Math.max(2, Runtime.getRuntime().availableProcessors() / 2))
                .numBatch(512)
                .mirostat(2)
                .mirostatTau(3.0f)
                .mirostatEta(0.05f)
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
                .temperature(0.3)
                .topP(0.85)
                .topK(20)
                .seed(42)
                .maxToken(maxToken)
                .repetitionPenalty(1.2)
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
    protected ChatOptions buildDeepSeekCompanionOptions() {
        return DeepSeekChatOptions.builder()
                .model(deepseekModelName)
                .temperature(0.3)
                .topP(0.85)
                .maxTokens(maxToken)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.TEXT)
                        .build())
                .stop(List.of("\n\n\n", "```", "【", "】"))
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
                .topK(20)
                .topP(0.85)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
                .temperature(0.3)
                .maxTokens(maxToken)
                .build();
    }

    @Retryable(
            label = "conversation-name-generation-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCall(chatModel, userPrompt);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doStream(chatModel, userPrompt);
    }
}