package org.lixiyun.server.ai.model.diagnosis.input;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization.SymptomOriginalItem;
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
 * 症状语义归一化模型
 * 对规则词典无法匹配的个性化/小众症状表述做模型语义归一化解析
 *
 * @author lixiyun
 * @since 2026-08-10 17:28
 */
@Slf4j
@Component
public class SymptomNormalizeModel extends BaseModel {

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final int maxToken = 2048;

    private final String systemPrompt = """
            你是一个心理健康领域的症状语义归一化助手。你的任务是将用户口语化的症状表述映射到标准症状术语。

            ## 核心约束
            1. **范围限定**：你只能从给定的「标准症状库」中选择最匹配的标签，严禁自创术语。如果标准症状库中没有合适的匹配项，对应映射的matchedTermId设为null。
            2. **语义严谨**：严格基于原文语义匹配，严禁过度推断、延伸用户未提及的症状。
            3. **格式固定**：按SymptomNormalizeResult的JSON结构输出，包含termList和termOriginalMapping两个字段。

            ## 输出格式
            ```json
            {
              "termList": [
                {
                  "symptomDict": {"id": 1, "symptomTerm": "入睡困难"},
                  "matchConfidence": 0.85
                }
              ],
              "termOriginalMapping": {
                "1": [
                  {"originalText": "睡不着", "matchedTermId": 1}
                ]
              }
            }
            ```

            ## 字段说明
            - termList：匹配到的标准术语列表，每项包含symptomDict（只需填id和symptomTerm）和matchConfidence（0-1置信度）
            - termOriginalMapping：术语ID到原文的映射，key为标准术语ID（字符串），value为该术语匹配到的所有原文列表
            - 如果某条表述无法匹配到任何标准术语，放入termOriginalMapping时key使用"unmatched"，matchedTermId设为null

            ## 注意事项
            - matchConfidence范围0-1，表示语义匹配置信度
            - 每条待匹配原文必须出现在termOriginalMapping中，不可遗漏
            - symptomDict中的id必须与标准症状库中的ID完全一致
            """;

    @Override
    protected String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "symptomNormalize";
    }

    @Override
    protected String getAgentDescription() {
        return "症状语义归一化";
    }

    @Override
    protected Class<?> getOutputType() {
        return SymptomNormalizeModelResult.class;
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
            label = "symptom-normalize-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCall(chatModel, userPrompt);
    }

    @Retryable(
            label = "symptom-normalize-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public SymptomNormalizeModelResult callForResult(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doStream(chatModel, userPrompt);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SymptomNormalizeModelResult {

        private static final long serialVersionUID = 1L;

        private List<SymptomOriginalItem> termList;
    }
}