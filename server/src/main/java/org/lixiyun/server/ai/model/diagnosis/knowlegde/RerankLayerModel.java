package org.lixiyun.server.ai.model.diagnosis.knowlegde;

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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.util.Map;

/**
 * 重排层模型
 * 对向量召回的粗结果做二次校验，基于大模型语义判断筛选高相关切片
 *
 * @author lixiyun
 * @since 2026-08-12 14:28
 */
@Slf4j
@Component
public class RerankLayerModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 4096;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的知识重排打分助手。你的任务是对向量检索召回的知识切片做二次语义校验，为每个切片评估与用户症状和诉求的相关性分值。

            ## 输入结构说明
            用户提示词按以下三段式结构组织，请依此提取打分所需信息：
            1. **用户信息**：包含标准症状列表、主导情绪、核心诉求，以及各维度的筛选依据说明
            2. **候选切片**：按三类知识库分组列出，每条切片标注ID、向量相似度和内容文本
            3. **打分指引**：三个打分维度的定义与权重说明，指导你如何综合评估

            ## 核心职责
            1. **语义相关性打分**：为每个候选切片评估与用户情况的相关性，输出0~1区间的分值，0表示完全无关，1表示高度相关
            2. **区分度打分**：不同切片之间应有明显分值差异，最相关的切片应接近1.0，不相关的应接近0.0

            ## 三类知识库的打分标准
            1. **症状库**：以症状匹配度为核心维度，评估切片内容与用户标准症状的语义关联程度，描述的症状表现是否与用户情况匹配
            2. **诊断标准库**：以症状匹配度和情绪关联度为核心维度，评估切片内容与用户症状+主导情绪对应的诊断条目相关程度，能否辅助判断用户心理状态；主导情绪是诊断标准库的关键筛选信号，切片对应的诊断条目应与用户情绪状态存在因果或关联关系
            3. **干预方案库**：以诉求匹配度为核心维度，评估切片内容与用户核心诉求的语义关联程度，能否提供针对性的干预方法或调节策略

            ## 打分维度
            请综合以下三个维度为每个候选切片评估相关性分值：
            1. **症状匹配度**（核心维度）：切片内容与用户标准症状的重合程度，命中症状关键词越多、语义越贴近，分值越高
            2. **诉求匹配度**（干预库核心维度）：干预方案切片与用户核心诉求的语义关联程度，能否提供针对性的干预方法
            3. **向量相似度**（参考维度）：用户提示词中标注的向量相似度分数，分数越高说明语义大方向越相关，但必须结合上述业务维度独立判断，避免仅依赖向量分数

            ## 打分约束
            - 仅对与用户情况确实相关的切片给予高分（≥0.5），不相关的切片给予低分（<0.5）
            - 如果切片内容与用户症状/诉求完全无关，给予0分
            - 同一知识库内高度相似的切片，只对最相关的那条给高分，其余降分
            - 分值应体现差异：最相关0.8~1.0，中等相关0.5~0.8，低相关0.2~0.5，无关0~0.2
            - 你的打分将作为主要权重（70%）与向量相似度（30%）加权计算综合分，因此请确保分值具有足够的区分度，避免大量切片聚集在同一分数段

            ## 输出格式
            严格按以下JSON结构输出，key为候选切片的ID（数字字符串），value为相关性分值（0~1浮点数）：
            ```json
            {
              "symptomScores": {"101": 0.9, "102": 0.6, "103": 0.2},
              "diagnosisScores": {"201": 0.85, "202": 0.3},
              "interventionScores": {"301": 0.8, "302": 0.5, "303": 0.1}
            }
            ```

            ## 注意事项
            - 切片ID必须从用户提示词的候选切片列表中选取，不可自行编造
            - 每个候选切片都必须给出分值，不要遗漏
            - 如果用户提示词中某类知识库无候选切片，对应对象输出为空{}
            - 严格按JSON格式输出，不要输出任何其他内容
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
        return "rerankLayer";
    }

    @Override
    protected String getAgentDescription() {
        return "重排层-语义筛选去噪与相关性排序";
    }

    @Override
    protected Class<?> getOutputType() {
        return RerankLayerResult.class;
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
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return ChatOptions.builder()
                .topK(40)
                .topP(0.9)
                .frequencyPenalty(0.6)
                .presencePenalty(0.2)
                .temperature(temperature)
                .maxTokens(maxToken)
                .build();
    }

    @Retryable(
            label = "rerank-layer-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return call(chatModel, userPrompt, config, null);
    }

    @Retryable(
            label = "rerank-layer-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public RerankLayerResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config, runnableConfig);
    }

    public RerankLayerResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
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
    public static class RerankLayerResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private Map<Long, Double> symptomScores;
        private Map<Long, Double> diagnosisScores;
        private Map<Long, Double> interventionScores;
    }
}