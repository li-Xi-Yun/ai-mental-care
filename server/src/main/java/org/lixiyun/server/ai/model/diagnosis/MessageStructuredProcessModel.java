package org.lixiyun.server.ai.model.diagnosis;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.CoreInfoExtractResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 消息结构化处理模型
 * 调用轻量化大模型从对话中提取核心信息
 * @author lixiyun
 * @since 2026-08-10 14:52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageStructuredProcessModel {

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final int maxToken = 2048;

    private final String systemPrompt = "";

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder(ChatModel chatModel) {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        return ReactAgent.builder()
                .model(chatModel)
                .name("messageStructuredProcess")
                .description("消息结构化处理")
                .chatOptions(chatOptions(chatModel))
                .enableLogging(false);
    }

    private ChatOptions chatOptions(ChatModel chatModel) {
        if (chatModel instanceof OllamaChatModel) {
            return buildOllamaCompanionOptions();
        } else if (chatModel instanceof DashScopeChatModel) {
            return buildDashScopeCompanionOptions();
        } else if (chatModel instanceof DeepSeekChatModel) {
            return buildDeepSeekCompanionOptions();
        } else {
            return buildDefaultCompanionOptions();
        }
    }

    private ChatOptions buildOllamaCompanionOptions() {
        return OllamaChatOptions.builder()
                .model(ollamaModelName)
                .temperature(0.2)
                .topK(30)
                .topP(0.85)
                .numPredict(maxToken)
                .repeatPenalty(1.2)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
                .repeatLastN(50)
                .numCtx(maxToken)
                .numThread(Runtime.getRuntime().availableProcessors())
                .format("json")
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

    private ChatOptions buildDashScopeCompanionOptions() {
        return DashScopeChatOptions.builder()
                .model(dashscopeModelName)
                .temperature(0.2)
                .topP(0.85)
                .topK(40)
                .seed(42)
                .maxToken(maxToken)
                .repetitionPenalty(1.2)
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(DashScopeResponseFormat.Type.JSON_OBJECT)
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

    private ChatOptions buildDeepSeekCompanionOptions() {
        return DeepSeekChatOptions.builder()
                .model(deepseekModelName)
                .temperature(0.2)
                .topP(0.85)
                .maxTokens(maxToken)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_OBJECT)
                        .build())
                .logprobs(false)
                .topLogprobs(null)
                .build();
    }

    private ChatOptions buildDefaultCompanionOptions() {
        return ChatOptions.builder()
                .topK(40)
                .topP(0.9)
                .frequencyPenalty(0.6)
                .presencePenalty(0.2)
                .temperature(0.4)
                .maxTokens(maxToken)
                .build();
    }

    @Retryable(
            label = "message-structured-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return reactAgentBuilder(chatModel)
                .systemPrompt(systemPrompt)
                .outputType(CoreInfoExtractResult.class)
                .build()
                .call(userPrompt);
    }
}