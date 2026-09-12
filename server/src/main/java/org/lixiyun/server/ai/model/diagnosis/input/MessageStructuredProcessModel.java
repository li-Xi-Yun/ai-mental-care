package org.lixiyun.server.ai.model.diagnosis.input;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.CoreInfoExtractResult;
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

/**
 * 消息结构化处理模型
 * 调用轻量化大模型从对话中提取核心信息
 *
 * @author lixiyun
 * @since 2026-08-10 14:52
 */
@Slf4j
@Component
public class MessageStructuredProcessModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是心理健康领域的对话核心信息提取助手，任务是从用户与AI的心理咨询对话中提取结构化核心信息。

            ## 输入格式
            对话按轮次组织，格式如下：
            - 轮次标记：【第N轮】（N为对话轮次号，提取roundNum时直接使用此编号）
            - 消息标记：[user] 表示用户消息，[assistant] 表示AI回复消息
            示例：
            【第1轮】
            [user] 我最近总是失眠
            [assistant] 失眠持续多长时间了？

            ## 核心约束
            1. **忠实原文**：所有提取内容必须严格基于对话原文，严禁编造、推断或补充用户未提及的信息。
            2. **完整覆盖**：确保不遗漏用户明确表达的核心诉求、关键事件、症状表述和背景信息。
            3. **角色区分**：仅从[user]消息中提取症状、事件和诉求；[assistant]消息仅作为理解对话上下文的参考，不从中提取任何信息。
            4. **逐轮记录**：同一症状若在不同轮次中出现，每轮均独立记录一条，保留完整的症状演变轨迹。

            ## 字段说明与边界界定
            - **coreAppeal**（String）：用一句话概括用户本次咨询最核心的心理困扰与求助需求。聚焦于"用户最想解决的问题"本身，而非问题成因。例：用户说"因为工作压力太大导致失眠"，coreAppeal应为"因工作压力导致严重失眠，寻求改善方法"而非仅"失眠"。
            - **keyEventTimeline**（Array）：按轮次顺序提取对用户心理状态产生显著影响的应激事件，每项包含：
              - roundNum：事件出现的对话轮次号，取自输入的【第N轮】标记
              - eventDesc：事件描述，尽量保留用户原文表述
              判定标准：导致或加剧心理困扰的负面生活事件（如失业、丧亲、婚变、校园霸凌等）。日常琐事不提取。无关键事件时返回空数组[]。
            - **symptomOriginalList**（Array）：提取用户提到的所有心理/情绪/躯体症状原始表述，每项包含：
              - roundNum：症状出现的对话轮次号，取自输入的【第N轮】标记
              - originalText：用户症状的原文表述，必须逐字保留用户措辞，不得改写或概括
              症状范围包括但不限于：情绪低落、焦虑、恐惧、愤怒、失眠、嗜睡、食欲异常、躯体不适、注意力困难、记忆减退、强迫思维/行为、社交退缩、惊恐发作、解离症状、自伤/自杀念头等。无症状时返回空数组[]。
            - **backgroundSummary**（String | null）：总结用户的社会支持系统（家庭/朋友/同事支持程度）、生活环境、人际关系、工作学业状况等背景信息。与coreAppeal的区别：coreAppeal是核心困扰本身，backgroundSummary是困扰发生的外部环境与支持条件。若无明确背景信息，设为null。

            ## 输出格式
            严格按以下JSON结构输出，不要添加markdown代码块标记：
            {
              "coreAppeal": "用户核心诉求的一句话概括",
              "keyEventTimeline": [
                {"roundNum": 1, "eventDesc": "关键事件描述原文"}
              ],
              "symptomOriginalList": [
                {"roundNum": 2, "originalText": "用户症状原始表述"}
              ],
              "backgroundSummary": "用户背景信息总结"
            }
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
        return "messageStructuredProcess";
    }

    @Override
    protected String getAgentDescription() {
        return "消息结构化处理";
    }

    @Override
    protected Class<?> getOutputType() {
        return CoreInfoExtractResult.class;
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
            label = "message-structured-process-model",
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
            label = "message-structured-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public CoreInfoExtractResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config, runnableConfig);
    }

    public CoreInfoExtractResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
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
}