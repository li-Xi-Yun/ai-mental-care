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

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final int maxToken = 2048;

    private final String systemPrompt = """
            你是一个心理健康领域的保护性因素分析助手。你的任务是根据用户的对话内容，评估社会支持水平、识别保护性因素/心理资源并分析用户的应对方式。

            ## 核心约束
            1. **支持水平**：综合判断用户的社会支持水平，使用标准化的等级表述
            2. **保护性因素**：识别用户拥有的保护性因素和心理资源，用逗号分隔
            3. **应对方式**：分析用户面对压力时的应对方式
            4. **格式固定**：严格按ProtectiveFactorResult的JSON结构输出

            ## 输出格式
            ```json
            {
              "socialSupportLevel": 1,
              "protectiveFactors": "家人支持,朋友陪伴,有兴趣爱好,自我调节能力强",
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
            - protectiveFactors：保护性因素/心理资源，逗号分隔，如"家人支持,朋友陪伴,有兴趣爱好,自我调节能力强,运动习惯,宗教信仰,宠物陪伴"
            - copingStyle：用户的应对方式，从"积极解决/回避/倾诉/压抑/运动调节/寻求专业帮助/转移注意力"中选择最主导的方式

            ## 注意事项
            - socialSupportLevel应基于用户实际描述的社会关系和支持情况判断
            - protectiveFactors应客观识别用户拥有的积极资源，不可编造
            - copingStyle应反映用户最典型、最常用的应对方式
            - 如果用户未提及相关内容，根据上下文合理推断，但需标注不确定性
            """;

    @Override
    protected String getSystemPrompt() {
        return systemPrompt;
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
    protected ChatOptions buildOllamaCompanionOptions() {
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

    @Override
    protected ChatOptions buildDashScopeCompanionOptions() {
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

    @Override
    protected ChatOptions buildDeepSeekCompanionOptions() {
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

    @Override
    protected ChatOptions buildDefaultCompanionOptions() {
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
            label = "protective-factor-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCall(chatModel, userPrompt);
    }

    @Retryable(
            label = "protective-factor-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public ProtectiveFactorResult callForResult(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doStream(chatModel, userPrompt);
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