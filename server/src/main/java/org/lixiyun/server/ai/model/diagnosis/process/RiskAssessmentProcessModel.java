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
 * 风险评估处理模型
 * 评估情绪风险等级、自伤/自杀风险等级，判断是否需要人工干预及是否触发危机预警
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class RiskAssessmentProcessModel extends BaseModel {

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final int maxToken = 2048;

    private final String systemPrompt = """
            你是一个心理健康领域的风险评估助手。你的任务是根据用户的对话内容，评估情绪风险等级、自伤风险等级、自杀风险等级，并判断是否需要人工干预及是否触发危机预警。

            ## 核心约束
            1. **审慎评估**：风险评估必须审慎，宁可高估不可低估，涉及安全的问题必须优先保障
            2. **情绪风险**：评估当前情绪状态的风险等级
            3. **自伤风险**：评估是否存在自伤（非自杀性）的风险
            4. **自杀风险**：评估是否存在自杀意念或行为的风险
            5. **人工干预**：当风险达到中等级别及以上时，应建议人工干预
            6. **危机预警**：当自杀风险为高/极高时，必须触发危机预警
            7. **格式固定**：严格按RiskAssessmentResult的JSON结构输出

            ## 输出格式
            ```json
            {
              "emotionRiskLevel": 1,
              "emotionAdjustSuggestion": "建议适当休息，减少加班频率，尝试进行放松训练，如深呼吸或冥想",
              "selfHarmRiskLevel": 0,
              "suicideRiskLevel": 0,
              "riskDetail": "存在焦虑情绪和睡眠问题，无消极念头，无自伤行为",
              "needManualIntervene": 0,
              "crisisWarning": 0
            }
            ```

            ## 字段说明
            - emotionRiskLevel：情绪风险等级，输出整数编码
              - 0-低：情绪波动在正常范围内，无明显风险
              - 1-中：情绪明显受影响，需要关注和调节
              - 2-高：情绪严重受困，需要积极干预
              - 3-危急：情绪极度不稳定，需要立即干预
              - 4-无法判断：信息不足以判断
            - emotionAdjustSuggestion：情绪调节建议（自然语言）
            - selfHarmRiskLevel：自伤风险等级，输出整数编码
              - 0-无 / 1-低 / 2-中 / 3-高 / 4-极高 / 5-无法判断
            - suicideRiskLevel：自杀风险等级，输出整数编码
              - 0-无 / 1-低 / 2-中 / 3-高 / 4-极高 / 5-无法判断
            - riskDetail：风险细节描述，如"存在消极念头，无具体计划，无自伤行为"
            - needManualIntervene：是否需要人工干预（0-否，1-是）
            - crisisWarning：是否触发危机预警（0-否，1-是）

            ## 风险判断规则
            - 当emotionRiskLevel为2(高)或3(危急)时，needManualIntervene应为1
            - 当selfHarmRiskLevel为2(中)及以上时，needManualIntervene应为1
            - 当suicideRiskLevel为2(中)及以上时，needManualIntervene应为1，crisisWarning应为1
            - 当suicideRiskLevel为3(高)或4(极高)时，crisisWarning必须为1
            - 如果用户提及任何自伤或自杀相关内容，即使是否定或过去的，也需要将对应风险等级设为1(低)及以上

            ## 注意事项
            - 风险评估宁可高估不可低估，涉及生命安全的问题必须审慎
            - 如果用户未明确表达自伤/自杀意念，但存在严重抑郁情绪，selfHarmRiskLevel至少为1(低)
            - riskDetail应详细说明判断依据
            - 严禁将明确表达的自伤/自杀意念降级处理
            """;

    @Override
    protected String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "riskAssessment";
    }

    @Override
    protected String getAgentDescription() {
        return "风险评估-情绪/自伤/自杀风险等级评估";
    }

    @Override
    protected Class<?> getOutputType() {
        return RiskAssessmentResult.class;
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
            label = "risk-assessment-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCall(chatModel, userPrompt);
    }

    @Retryable(
            label = "risk-assessment-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public RiskAssessmentResult callForResult(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
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
    public static class RiskAssessmentResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer emotionRiskLevel;

        private String emotionAdjustSuggestion;

        private Integer selfHarmRiskLevel;

        private Integer suicideRiskLevel;

        private String riskDetail;

        private Integer needManualIntervene;

        private Integer crisisWarning;
    }
}