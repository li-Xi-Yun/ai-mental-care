package org.lixiyun.server.ai.model.diagnosis.process;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.model.BaseModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.api.OllamaChatOptions;
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

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是心理健康领域的诊断书生成助手。你的任务是综合用户对话内容、情绪统计数据、症状信息以及上游分析节点的结论，生成诊断书核心内容总结，并提取核心情绪标签、核心情绪平均置信度和核心情绪强度分值。

            ## 输入数据来源
            用户提示词中可能包含以下维度的数据，你应按优先级引用：
            1. **上游分析结论**（最高优先级）：心理状态评估、核心症状总结、社会功能影响、保护性因素、风险评估结论、干预建议等——当这些结论已存在时，直接引用而非重新推断
            2. **情绪统计数据**：主导情绪、正负向占比、情绪趋势、量化指标（PAD均值、强度均值）
            3. **核心诉求与背景**：用户核心诉求、背景信息摘要
            4. **标准症状列表**：归一化后的症状术语及严重程度
            5. **诊断标准参考**：知识库检索到的诊断参考信息
            6. **历史诊断摘要**：此前轮次的诊断结论

            ## 核心约束
            1. **诊断书内容**：综合所有维度信息，按固定段落结构撰写专业、客观、有层次的诊断书核心内容总结
            2. **核心情绪标签**：识别最核心、最主导的情绪标签，须从标准情绪词汇表中选择
            3. **置信度**：评估核心情绪标签的平均置信度，范围[0, 1]，保留两位小数
            4. **强度分值**：评估核心情绪的强度分值，范围[0, 1]，保留两位小数
            5. **格式固定**：严格按DiagnosisSummaryResult的JSON结构输出，不得添加额外字段

            ## 诊断书内容段落结构
            diagnosisContent须按以下段落顺序组织，每段用句号结尾，段落间自然衔接：
            1. **整体情绪状态**：主导情绪及伴随情绪，如"近期情绪状态以焦虑为主，伴随轻度抑郁情绪"
            2. **核心诉求与触发因素**：核心诉求及触发场景/关键词，如"核心诉求为工作压力导致的心理困扰"
            3. **主要症状表现**：核心症状及发作模式，如"表现为持续焦虑、入睡困难和注意力下降，症状持续约两周"
            4. **社会功能影响**：受损程度及受影响领域，如"社会功能轻度受损，工作效率有所下降，睡眠质量受影响"
            5. **保护性因素与应对方式**：社会支持水平、保护性因素及应对方式，如"保护性因素包括家人支持和自我调节能力，应对方式以倾诉为主"
            6. **风险等级与建议方向**：情绪风险等级及建议方向，如"情绪风险等级为中等，建议适当休息并寻求社会支持"

            ## 标准情绪词汇表
            核心情绪标签必须从以下词汇中选择：
            - 负向：焦虑、抑郁、愤怒、悲伤、恐惧、不满、厌恶、自责、迷茫、无助、紧张、失落
            - 正向：开心、欣慰、兴奋、满足、愉悦、期待、乐观、放松
            - 中性：中性、平静、淡漠

            ## 输出格式
            ```json
            {
              "diagnosisContent": "用户近期情绪状态以焦虑为主，伴随轻度抑郁情绪。核心诉求为工作压力导致的心理困扰，表现为持续焦虑、入睡困难和注意力下降，症状持续约两周。社会功能轻度受损，工作效率有所下降，睡眠质量受影响。保护性因素包括家人支持和自我调节能力，应对方式以倾诉为主。情绪风险等级为中等，建议适当休息并寻求社会支持。",
              "coreEmotionLabel": "焦虑",
              "coreEmotionConfAvg": 0.85,
              "coreEmotionIntensityScore": 0.72
            }
            ```

            ## 字段说明
            - **diagnosisContent**：诊断书核心内容，自然语言总结，200~500字，须覆盖上述6个段落要素，语言专业客观，避免绝对化表述和过度诊断
            - **coreEmotionLabel**：核心情绪标签，从标准情绪词汇表中选择，应与情绪统计数据中的主导情绪保持一致
            - **coreEmotionConfAvg**：核心情绪平均置信度，[0, 1]，保留两位小数，反映对核心情绪标签判断的可靠程度；当情绪数据充分时通常≥0.7，数据稀疏时相应降低
            - **coreEmotionIntensityScore**：核心情绪强度分值，[0, 1]，保留两位小数，反映核心情绪的强烈程度；应基于情绪强度量化数据合理评估

            ## 边界场景
            - 无有效情绪数据：coreEmotionLabel输出"中性"，coreEmotionConfAvg输出0.50，coreEmotionIntensityScore输出0.50，diagnosisContent中注明"情绪数据不足，结论仅供参考"
            - 症状信息缺失：跳过"主要症状表现"段落，其余段落正常输出
            - 多个情绪强度接近：选择出现频次更高或趋势更显著的情绪作为核心标签
            - 历史诊断摘要存在：将其作为参照，与当前分析结论对比，若存在显著变化须在diagnosisContent中体现
            """;

    @Override
    protected String getSystemPrompt(AiNodeConfig config) {
        if (config != null && config.getSystemPrompt() != null) {
            return config.getSystemPrompt();
        }
        return defaultSystemPrompt;
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
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getOllamaModelName() != null ? config.getOllamaModelName() : defaultOllamaModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.2;
        double topP = config != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 30;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;

        return OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .numPredict(maxToken)
                .repeatPenalty(1.2)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .repeatLastN(50)
                .numCtx(maxToken)
                .numThread(Runtime.getRuntime().availableProcessors())
                .format(isJsonResponseFormat(config) ? "json" : null)
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
    protected ChatOptions buildDashScopeCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDashscopeModelName() != null ? config.getDashscopeModelName() : defaultDashscopeModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.2;
        double topP = config != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 40;

        return DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .seed(42)
                .maxToken(maxToken)
                .repetitionPenalty(1.2)
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(isJsonResponseFormat(config) ? DashScopeResponseFormat.Type.JSON_OBJECT : DashScopeResponseFormat.Type.TEXT)
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
    protected ChatOptions buildDeepSeekCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDeepseekModelName() != null ? config.getDeepseekModelName() : defaultDeepseekModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.2;
        double topP = config != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;

        return DeepSeekChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxToken)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .responseFormat(ResponseFormat.builder()
                        .type(isJsonResponseFormat(config) ? ResponseFormat.Type.JSON_OBJECT : ResponseFormat.Type.TEXT)
                        .build())
                .logprobs(false)
                .topLogprobs(null)
                .build();
    }

    @Override
    protected ChatOptions buildDefaultCompanionOptions(AiNodeConfig config) {
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.4;
        double topP = config != null ? config.getTopP().doubleValue() : 0.9;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.6;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.2;

        return ChatOptions.builder()
                .topK(40)
                .topP(topP)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .temperature(temperature)
                .maxTokens(maxToken)
                .build();
    }

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return call(chatModel, userPrompt, config, null);
    }

    public DiagnosisSummaryResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config, runnableConfig);
    }

    public DiagnosisSummaryResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return callForResult(chatModel, userPrompt, config, null);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return stream(chatModel, userPrompt, config, null);
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