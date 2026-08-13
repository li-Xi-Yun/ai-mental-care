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
 * 社会功能影响评估处理模型
 * 评估用户社会功能受损程度、受影响领域及对日常生活的影响
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class SocialFunctionImpactProcessModel extends BaseModel {

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final int maxToken = 2048;

    private final String systemPrompt = """
            你是一个心理健康领域的社会功能影响评估助手。你的任务是根据用户的对话内容，评估社会功能受损程度、识别受影响的具体领域并描述对日常生活的影响。

            ## 核心约束
            1. **受损程度**：综合判断社会功能受损程度，使用标准化的等级表述
            2. **影响领域**：识别受影响的具体生活领域，用逗号分隔
            3. **生活影响**：用自然语言描述对日常生活的具体影响
            4. **格式固定**：严格按SocialFunctionImpactResult的JSON结构输出

            ## 输出格式
            ```json
            {
              "socialFunctionImpact": "中度受损",
              "impactDomains": "工作效率下降,睡眠受影响,社交减少,食欲变差",
              "dailyLifeInfluence": "用户表示工作效率明显下降，经常无法集中注意力；睡眠质量差，入睡困难；减少了与朋友的社交活动；食欲明显下降"
            }
            ```

            ## 字段说明
            - socialFunctionImpact：社会功能受损程度，从"无影响/轻度受损/中度受损/重度受损"中选择
              - 无影响：社会功能基本正常，日常生活未受明显影响
              - 轻度受损：偶有影响，但整体可维持正常生活
              - 中度受损：明显影响工作、学习或社交，但尚能勉强维持
              - 重度受损：严重影响日常生活，无法正常工作或社交
            - impactDomains：受影响的具体领域，逗号分隔，如"工作效率下降,睡眠受影响,社交减少,食欲变差,学习困难,家庭关系紧张"
            - dailyLifeInfluence：对日常生活影响的自然语言描述，应具体、有依据

            ## 注意事项
            - socialFunctionImpact的判断应基于用户实际描述的功能损害程度
            - impactDomains中每个领域应是独立的影响项
            - dailyLifeInfluence应结合用户原话进行概括，不可脱离对话内容
            - 如果用户未提及明显的功能损害，socialFunctionImpact应为"无影响"
            """;

    @Override
    protected String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "socialFunctionImpact";
    }

    @Override
    protected String getAgentDescription() {
        return "社会功能影响评估";
    }

    @Override
    protected Class<?> getOutputType() {
        return SocialFunctionImpactResult.class;
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
            label = "social-function-impact-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCall(chatModel, userPrompt);
    }

    @Retryable(
            label = "social-function-impact-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public SocialFunctionImpactResult callForResult(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
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
    public static class SocialFunctionImpactResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String socialFunctionImpact;

        private String impactDomains;

        private String dailyLifeInfluence;
    }
}