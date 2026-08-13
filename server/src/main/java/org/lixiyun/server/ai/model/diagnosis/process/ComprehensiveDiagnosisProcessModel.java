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
import java.util.Map;

/**
 * 情绪综合分析组处理模型
 * 分析整体情绪趋势、负向情绪细分占比和正向情绪细分占比
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class ComprehensiveDiagnosisProcessModel extends BaseModel {

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final int maxToken = 2048;

    private final String systemPrompt = """
            你是一个心理健康领域的情绪综合分析助手。你的任务是根据用户的情绪统计数据，分析整体情绪趋势、负向情绪细分占比和正向情绪细分占比。

            ## 核心约束
            1. **负向细分**：将负向情绪按具体类别拆分，计算各类别占比，所有负向情绪占比之和应为1.0
            2. **正向细分**：将正向情绪按具体类别拆分，计算各类别占比，所有正向情绪占比之和应为1.0
            3. **格式固定**：严格按EmotionComprehensiveResult的JSON结构输出

            ## 输出格式
            ```json
            {
              "negativeEmotionDetail": {"焦虑": 0.45, "愤怒": 0.25, "悲伤": 0.10, "恐惧": 0.10, "厌恶": 0.10},
              "positiveEmotionDetail": {"开心": 0.30, "欣慰": 0.15, "放松": 0.05, "期待": 0.25, "平静": 0.25}
            }
            ```

            ## 字段说明
            - negativeEmotionDetail：负向情绪细分占比，key为具体负向情绪标签（如焦虑、愤怒、悲伤、恐惧、厌恶等），value为该情绪在所有负向情绪中的占比（0~1，所有值之和为1.0）
            - positiveEmotionDetail：正向情绪细分占比，key为具体正向情绪标签（如开心、欣慰、放松、期待、平静等），value为该情绪在所有正向情绪中的占比（0~1，所有值之和为1.0）

            ## 注意事项
            - negativeEmotionDetail中所有value之和必须为1.0
            - positiveEmotionDetail中所有value之和必须为1.0
            - 如果用户无负向情绪表现，negativeEmotionDetail输出为空对象{}
            - 如果用户无正向情绪表现，positiveEmotionDetail输出为空对象{}
            - 情绪标签应使用标准的中文情绪词汇，不可自创
            """;

    @Override
    protected String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "emotionComprehensive";
    }

    @Override
    protected String getAgentDescription() {
        return "情绪综合分析-整体趋势与正负向细分占比";
    }

    @Override
    protected Class<?> getOutputType() {
        return EmotionComprehensiveResult.class;
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
            label = "emotion-comprehensive-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCall(chatModel, userPrompt);
    }

    @Retryable(
            label = "emotion-comprehensive-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public EmotionComprehensiveResult callForResult(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
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
    public static class EmotionComprehensiveResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private Map<String, Double> negativeEmotionDetail;

        private Map<String, Double> positiveEmotionDetail;
    }
}