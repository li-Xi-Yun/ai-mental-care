package org.lixiyun.server.ai.model;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.ai.infrastructure.storage.ConversationHistoryMessagesStorage;
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

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-07-15 15:32
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryAnalysisCompressionModel {

    private final ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    /**
     * 系统提示词：定义AI的角色和行为准则（历史情绪分析压缩专家）
     */
    // todo 之后要提取这个系统提示词到配置文件中
    private final String systemPrompt = """
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

    /** 最大生成token数 */
    private static final int maxToken = 2000;

    /**
     * 构建ReactAgent构建器
     *
     * @param chatModel 大模型实例
     * @return 配置完成的Agent构建器
     * @throws BusinessException 当模型为null时抛出
     */
    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuild(ChatModel chatModel) {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        return ReactAgent.builder()
                .model(chatModel)
                .name("historyAnalysisCompression")
                .description("专业历史情绪分析数据压缩助手")
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
                .keepAlive("30m")

                .temperature(0.15)
                .topK(20)
                .topP(0.82)
                .numPredict(maxToken)
                .seed(42)

                .repeatPenalty(1.25)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
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

    private ChatOptions buildDashScopeCompanionOptions() {
        return DashScopeChatOptions.builder()

                .model(dashscopeModelName)
                .temperature(0.15)
                .topP(0.82)
                .topK(20)
                .seed(42)
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

    private ChatOptions buildDeepSeekCompanionOptions() {
        return DeepSeekChatOptions.builder()

                .model(deepseekModelName)
                .temperature(0.15)
                .topP(0.82)
                .maxTokens(maxToken)

                .frequencyPenalty(0.7)
                .presencePenalty(0.3)

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

    private ChatOptions buildDefaultCompanionOptions() {
        return ChatOptions.builder()
                .topK(20)
                .topP(0.82)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
                .temperature(0.15)
                .maxTokens(maxToken)
                .build();
    }

    @Retryable(
            label = "history-analysis-compression-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return reactAgentBuild(chatModel)
                .systemPrompt(systemPrompt).build()
                .call(userPrompt);
    }

}