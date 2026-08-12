package org.lixiyun.server.ai.model.diagnosis;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

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
@RequiredArgsConstructor
public class RerankLayerModel {

    private final String deepseekModelName = "deepseek-chat";
    private final String ollamaModelName = "qwen3:7b-chat-thinking";
    private final String dashscopeModelName = "qwen-max";

    private final int maxToken = 4096;

    private final String systemPrompt = """
            你是一个心理健康领域的知识重排打分助手。你的任务是对向量检索召回的知识切片做二次语义校验，为每个切片评估与用户症状和诉求的相关性分值。

            ## 核心职责
            1. **语义相关性打分**：为每个候选切片评估与用户情况的相关性，输出0~1区间的分值，0表示完全无关，1表示高度相关
            2. **区分度打分**：不同切片之间应有明显分值差异，最相关的切片应接近1.0，不相关的应接近0.0

            ## 三类知识库的打分标准
            1. **症状库**：切片内容与用户标准症状的语义关联程度，描述的症状表现是否与用户情况匹配
            2. **诊断标准库**：切片内容与用户症状+情绪状态对应的诊断条目相关程度，能否辅助判断用户心理状态
            3. **干预方案库**：切片内容与用户核心诉求的语义关联程度，能否提供针对性的干预方法或调节策略

            ## 打分约束
            - 仅对与用户情况确实相关的切片给予高分（≥0.5），不相关的切片给予低分（<0.5）
            - 如果切片内容与用户症状/诉求完全无关，给予0分
            - 同一知识库内高度相似的切片，只对最相关的那条给高分，其余降分
            - 分值应体现差异：最相关0.8~1.0，中等相关0.5~0.8，低相关0.2~0.5，无关0~0.2

            ## 输出格式
            严格按以下JSON结构输出，key为切片ID（字符串），value为相关性分值（0~1浮点数）：
            ```json
            {
              "symptomScores": {"101": 0.9, "102": 0.6, "103": 0.2},
              "diagnosisScores": {"201": 0.85, "202": 0.3},
              "interventionScores": {"301": 0.8, "302": 0.5, "303": 0.1}
            }
            ```

            ## 注意事项
            - 切片ID必须从输入的候选切片列表中选取，不可自行编造
            - 每个候选切片都必须给出分值，不要遗漏
            - 如果某类知识库中没有高相关切片，对应对象输出为空{}
            - 严格按JSON格式输出，不要输出任何其他内容
            """;

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder(ChatModel chatModel) {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        return ReactAgent.builder()
                .model(chatModel)
                .name("rerankLayer")
                .description("重排层-语义筛选去噪与相关性排序")
                .chatOptions(chatOptions(chatModel))
                .enableLogging(false);
    }

    private ChatOptions chatOptions(ChatModel chatModel) {
        if (chatModel instanceof OllamaChatModel) {
            return buildOllamaCompanionOptions();
        } else if (chatModel instanceof DashScopeChatModel) {
            return buildDashScopeCompanionOptions();
        } else if (chatModel instanceof DeepSeekChatModel) {
            return buildDeepSeekCompanionOptions();
        } else {
            return buildDefaultCompanionOptions();
        }
    }

    private ChatOptions buildOllamaCompanionOptions() {
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

    private ChatOptions buildDashScopeCompanionOptions() {
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

    private ChatOptions buildDeepSeekCompanionOptions() {
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

    private ChatOptions buildDefaultCompanionOptions() {
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
            label = "rerank-layer-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public AssistantMessage call(ChatModel chatModel, String userPrompt) throws GraphRunnerException {
        return reactAgentBuilder(chatModel)
                .systemPrompt(systemPrompt)
                .outputType(RerankLayerResult.class)
                .build()
                .call(userPrompt);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RerankLayerResult implements Serializable {

        private static final long serialVersionUID = 1L;

        // key: 切片ID，value: 模型给出的相关性分值（0-1 区间，越高越相关）
        private Map<Long, Double> symptomScores;
        private Map<Long, Double> diagnosisScores;
        private Map<Long, Double> interventionScores;
    }
}