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
import java.math.BigDecimal;

/**
 * 诊断书生成处理模型
 * 综合所有输入数据，生成诊断书核心内容、核心情绪标签、核心情绪平均置信度和核心情绪强度分值
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class DiagnosisSummaryProcessModel extends BaseModel {

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final int maxToken = 2048;

    private final String systemPrompt = """
            你是一个心理健康领域的诊断书生成助手。你的任务是根据用户的对话内容、情绪统计数据、症状信息等多维度数据，生成诊断书核心内容总结，并提取核心情绪标签、核心情绪平均置信度和核心情绪强度分值。

            ## 核心约束
            1. **诊断书内容**：综合所有信息，用自然语言撰写一段专业、客观、有层次的诊断书核心内容总结
            2. **核心情绪标签**：从用户对话中识别出最核心、最主导的情绪标签
            3. **置信度**：评估核心情绪标签的平均置信度，范围0~1
            4. **强度分值**：评估核心情绪的强度分值，范围0~1
            5. **格式固定**：严格按DiagnosisSummaryResult的JSON结构输出

            ## 输出格式
            ```json
            {
              "diagnosisContent": "用户近期情绪状态以焦虑为主，伴随轻度抑郁情绪。核心诉求为工作压力导致的心理困扰，表现为持续焦虑、入睡困难和注意力下降。社会功能轻度受损，工作效率有所下降。保护性因素包括家人支持和自我调节能力，应对方式以倾诉为主。情绪风险等级为中等，建议适当休息并寻求社会支持。",
              "coreEmotionLabel": "焦虑",
              "coreEmotionConfAvg": 0.85,
              "coreEmotionIntensityScore": 0.72
            }
            ```

            ## 字段说明
            - diagnosisContent：诊断书核心内容，自然语言总结，应包含以下要素：
              - 整体情绪状态描述（主导情绪及伴随情绪）
              - 核心诉求与触发因素
              - 主要症状表现
              - 社会功能影响程度
              - 保护性因素与应对方式
              - 风险等级与建议方向
              - 语言应专业、客观、有层次，避免过度诊断
            - coreEmotionLabel：核心情绪标签，从标准中文情绪词汇中选择，如"焦虑/抑郁/愤怒/悲伤/恐惧/开心/中性/平静"等
            - coreEmotionConfAvg：核心情绪平均置信度，0~1之间，保留两位小数，反映对核心情绪标签判断的可靠程度
            - coreEmotionIntensityScore：核心情绪强度分值，0~1之间，保留两位小数，反映核心情绪的强烈程度

            ## 注意事项
            - diagnosisContent应综合所有维度信息，形成完整的诊断书总结
            - 核心情绪标签应与情绪统计数据中的主导情绪保持一致
            - coreEmotionConfAvg应基于情绪识别的置信度数据合理评估
            - coreEmotionIntensityScore应基于情绪强度数据合理评估
            - 诊断书内容应避免使用绝对化表述，保持专业审慎
            """;

    @Override
    protected String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "diagnosisSummary";
    }

    @Override
    protected String getAgentDescription() {
        return "诊断书生成-核心内容总结与核心情绪提取";
    }

    @Override
    protected Class<?> getOutputType() {
        return DiagnosisSummaryResult.class;
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
            label = "diagnosis-summary-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return doCall(chatModel, userPrompt);
    }

    @Retryable(
            label = "diagnosis-summary-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public DiagnosisSummaryResult callForResult(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
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
    public static class DiagnosisSummaryResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String diagnosisContent;

        private String coreEmotionLabel;

        private BigDecimal coreEmotionConfAvg;

        private BigDecimal coreEmotionIntensityScore;
    }
}