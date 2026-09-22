package org.lixiyun.server.ai.model.diagnosis;

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
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 诊断图意图识别模型
 * <p>调用轻量大模型判断当前轮次是否需要触发心理诊断流程。</p>
 * <p>模型仅回答 "1"（需要诊断）或 "0"（不需要诊断），不产生结构化输出。</p>
 *
 * @author lixiyun
 * @since 2026-09-18
 */
@Slf4j
@Component
public class IntentRecognitionModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-turbo";

    private final int defaultMaxToken = 16;

    private final String defaultSystemPrompt = """
            你是一个心理健康对话意图分析助手，作为诊断触发决策链路的兜底判断环节。
            你的前置规则模块已确认：当前对话片段包含实质性内容（非纯寒暄），但未检测到明确的求助信号或情绪剧烈变化，
            因此需要你做最终判断。

            你将收到以下结构化输入信息：
            【距上次诊断已过 X 轮】自上次心理诊断以来的轮次间隔（首次诊断时显示 0）
            【上次诊断风险等级】无 / 低风险 / 中风险 / 高风险 / 危急
            【用户近期情绪标签序列】最近几轮对话的情绪标签（如：焦虑 → 抑郁 → 焦虑）
            【当前用户消息】本轮用户发送的原始文本
            【区间对话片段】自上次诊断以来的完整 USER/ASSISTANT 对话记录

            决策逻辑（按优先级从高到低）：
            1. 风险兜底：若上次风险等级为"高风险"或"危急"，且距上次诊断已过 3 轮以上 → 输出 1
            2. 情绪恶化：若情绪标签序列呈恶化趋势（正向/中性 → 负向），且区间对话涉及具体困扰描述 → 输出 1
            3. 话题深入：若区间对话中用户持续深入探讨心理困扰（情绪、压力、人际、睡眠、创伤等），有倾诉和反思迹象 → 输出 1
            4. 首次诊断：若无诊断记录（风险等级为"无"），且区间对话已积累多轮有深度的心理相关对话 → 输出 1
            5. 排除场景：纯信息咨询（如询问系统功能）、日常闲聊、话题已自然转向轻松内容、或对话内容不涉及心理健康领域 → 输出 0

            仅输出一个数字，不要包含任何其他字符、标点或换行：
            - 1：需要触发心理诊断流程
            - 0：暂不需要触发诊断
            """;

    // ==================== Model 接口实现 ====================

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return call(chatModel, userPrompt, config, null);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return stream(chatModel, userPrompt, config, null);
    }


    public IntentRecognitionResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config, runnableConfig);
    }

    // ==================== BaseModel 抽象方法 ====================

    @Override
    protected String getSystemPrompt(AiNodeConfig config) {
        if (config != null && config.getSystemPrompt() != null) {
            return config.getSystemPrompt();
        }
        return defaultSystemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "intentRecognition";
    }

    @Override
    protected String getAgentDescription() {
        return "诊断意图识别";
    }

    @Override
    protected Class<?> getOutputType() {
        return IntentRecognitionResult.class;
    }

    @Override
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getOllamaModelName() != null
                ? config.getOllamaModelName() : defaultOllamaModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.1;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .numPredict(maxToken)
                .numCtx(512)
                .truncate(true)
                .seed(42)
                .build();
    }

    @Override
    protected ChatOptions buildDashScopeCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDashscopeModelName() != null
                ? config.getDashscopeModelName() : defaultDashscopeModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.1;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .maxToken(maxToken)
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(DashScopeResponseFormat.Type.TEXT)
                        .build())
                .build();
    }

    @Override
    protected ChatOptions buildDefaultCompanionOptions(AiNodeConfig config) {
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.1;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return ChatOptions.builder()
                .temperature(temperature)
                .maxTokens(maxToken)
                .topP(0.9)
                .build();
    }

    @Override
    protected ChatOptions buildDeepSeekCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDeepseekModelName() != null
                ? config.getDeepseekModelName() : defaultDeepseekModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.1;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;

        return DeepSeekChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .maxTokens(maxToken)
                .build();
    }

    // ==================== 结果内部类 ====================

    /**
     * 意图识别模型返回结果。
     *
     * <p>封装模型原始响应文本，提供 {@link #isNeedDiagnosis()} / {@link #isNoNeed()} 便捷判断方法。
     * 两个常量值从原先的 {@code IntentRecognitionNode} 移入，保持值域专一。</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentRecognitionResult {
        /** 模型输出 1 表示“需要触发诊断” */
        public static final String NEED_DIAGNOSIS = "1";
        /** 模型输出 0 表示“无需触发诊断” */
        public static final String NO_NEED = "0";

        /** 模型原始响应文本（trimmed） */
        private String response;

        /** @return true 表示需要触发诊断 */
        public boolean isNeedDiagnosis() {
            return NEED_DIAGNOSIS.equals(response);
        }

        /** @return true 表示无需触发诊断 */
        public boolean isNoNeed() {
            return NO_NEED.equals(response);
        }
    }
}