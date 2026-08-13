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
 * 心理状态与症状评估处理模型
 * 评估用户整体心理状态、总结核心症状、生成症状标签
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class PsychologicalStateProcessModel extends BaseModel {

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final int maxToken = 2048;

    private final String systemPrompt = """
            你是一个心理健康领域的心理状态与症状评估助手。你的任务是根据用户的对话内容，评估整体心理状态、总结核心症状并生成症状标签。

            ## 核心约束
            1. **状态评估**：综合判断用户整体心理状态，使用标准化的状态表述
            2. **症状总结**：用自然语言概括用户的核心症状表现，语言简洁准确
            3. **标签生成**：从症状总结中提取关键症状标签，用逗号分隔
            4. **格式固定**：严格按PsychologicalStateResult的JSON结构输出

            ## 输出格式
            ```json
            {
              "psychologicalState": "轻度焦虑状态",
              "symptomSummary": "持续情绪低落、兴趣减退、入睡困难、注意力下降",
              "symptomTags": "失眠,焦虑,自卑,易怒,兴趣减退,食欲下降"
            }
            ```

            ## 字段说明
            - psychologicalState：整体心理状态评估，从"适应不良/轻度焦虑状态/中度焦虑状态/抑郁情绪困扰/焦虑抑郁共病/人际敏感状态/应激反应/其他"中选择最匹配的
            - symptomSummary：核心症状总结（自然语言），概括用户的主要症状表现
            - symptomTags：症状标签集合，逗号分隔，如"失眠,焦虑,自卑,易怒,兴趣减退,食欲下降"

            ## 注意事项
            - psychologicalState应基于症状的严重程度和范围综合判断
            - symptomSummary应涵盖用户提及的所有显著症状，语言精练
            - symptomTags中的每个标签应是独立的症状关键词，不可包含修饰语
            - 严禁编造用户未提及的症状，所有评估需有对话依据
            """;

    @Override
    protected String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "psychologicalState";
    }

    @Override
    protected String getAgentDescription() {
        return "心理状态与症状评估";
    }

    @Override
    protected Class<?> getOutputType() {
        return PsychologicalStateResult.class;
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
            label = "psychological-state-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCall(chatModel, userPrompt);
    }

    @Retryable(
            label = "psychological-state-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public PsychologicalStateResult callForResult(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
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
    public static class PsychologicalStateResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String psychologicalState;

        private String symptomSummary;

        private String symptomTags;
    }
}