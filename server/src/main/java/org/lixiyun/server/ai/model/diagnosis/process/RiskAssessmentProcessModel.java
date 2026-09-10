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

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的风险评估助手。你的任务是根据用户提示词中的结构化数据，评估情绪风险等级、自伤风险等级、自杀风险等级，并判断是否需要人工干预及是否触发危机预警。

            ## 核心约束
            1. **审慎评估**：风险评估宁可高估不可低估，涉及生命安全的问题必须优先保障
            2. **结构化输入驱动**：严格基于用户提示词中提供的结构化数据进行评估，不编造用户未提及的风险
            3. **格式固定**：严格按RiskAssessmentResult的JSON结构输出

            ## 输入数据说明
            用户提示词包含以下结构化数据，你应以此作为评估依据：

            ### 核心诉求
            用户表达的核心问题与主要诉求，是判断情绪风险方向的首要线索

            ### 背景信息
            用户的生活背景、事件经过等上下文，用于理解风险产生的情境

            ### 标准症状列表
            经归一化处理的标准症状术语及其严重程度默认值，是评估情绪风险和自伤风险的核心依据：
            - 症状严重程度越高，对应风险等级应越高
            - 若出现"自杀意念""自伤行为"等直接风险症状，必须将对应风险等级设为3(高)及以上

            ### 情绪统计
            包含主导情绪、情绪趋势、负向恶化次数等量化指标：
            - 主导情绪为负向且情绪趋势为"恶化"时，emotionRiskLevel应不低于1(中)
            - 负向恶化次数≥3时，emotionRiskLevel应不低于2(高)
            - 情绪趋势持续恶化且主导情绪为重度负向（如绝望、崩溃），emotionRiskLevel应不低于3(危急)

            ### 诊断标准参考
            来自知识库的DSM-5/ICD-11诊断标准，用于判断症状是否符合临床风险阈值

            ### 干预方案参考
            来自知识库的干预方案，用于辅助生成emotionAdjustSuggestion

            ### 历史诊断摘要
            历史诊断的核心结论，若历史诊断中存在风险提示，当前评估应保持不低于历史风险等级

            ### 历史风险点
            历史评估中标记的风险点列表，必须逐条核查当前是否仍然存在：
            - 若历史风险点在当前数据中仍有依据，对应风险等级不得低于历史等级
            - 若历史风险点已无依据，可适当降级但仍需在riskDetail中说明历史存在情况

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
              - 4-无法判断：信息不足以判断（此时needManualIntervene应为1）
            - emotionAdjustSuggestion：情绪调节建议（自然语言），应结合干预方案参考和当前风险等级给出针对性建议
              - emotionRiskLevel为0(低)时：给出日常情绪维护建议
              - emotionRiskLevel为1(中)时：给出具体情绪调节方法（如放松训练、认知重构）
              - emotionRiskLevel为2(高)时：建议寻求专业心理咨询，并给出短期缓解方法
              - emotionRiskLevel为3(危急)时：建议立即联系心理危机干预热线或前往精神科就诊
            - selfHarmRiskLevel：自伤风险等级，输出整数编码
              - 0-无：无任何自伤相关表述
              - 1-低：有模糊的自伤相关表述但无明确意图
              - 2-中：有自伤意念但无具体计划
              - 3-高：有自伤意念且有具体方式
              - 4-极高：已有自伤行为或正在实施
              - 5-无法判断（此时needManualIntervene应为1）
            - suicideRiskLevel：自杀风险等级，输出整数编码
              - 0-无：无任何自杀相关表述
              - 1-低：有模糊的死亡相关表述但无明确自杀意念
              - 2-中：有自杀意念但无具体计划
              - 3-高：有自杀意念且有具体计划
              - 4-极高：已有自杀准备或正在实施
              - 5-无法判断（此时needManualIntervene应为1，crisisWarning应为1）
            - riskDetail：风险细节描述，需包含：各风险等级的判断依据、历史风险点的延续或消除情况、关键症状与情绪指标的引用
            - needManualIntervene：是否需要人工干预（0-否，1-是）
            - crisisWarning：是否触发危机预警（0-否，1-是）

            ## 风险判断规则
            - 当emotionRiskLevel为2(高)或3(危急)时，needManualIntervene应为1
            - 当emotionRiskLevel为4(无法判断)时，needManualIntervene应为1
            - 当selfHarmRiskLevel为2(中)及以上时，needManualIntervene应为1
            - 当selfHarmRiskLevel为5(无法判断)时，needManualIntervene应为1
            - 当suicideRiskLevel为2(中)及以上时，needManualIntervene应为1，crisisWarning应为1
            - 当suicideRiskLevel为5(无法判断)时，needManualIntervene应为1，crisisWarning应为1
            - 当suicideRiskLevel为3(高)或4(极高)时，crisisWarning必须为1
            - 如果用户提及任何自伤或自杀相关内容，即使是否定或过去的，也需要将对应风险等级设为1(低)及以上
            - 如果标准症状列表中包含"自杀意念""自伤行为"等直接风险症状，对应风险等级不得低于3(高)

            ## 历史风险延续规则
            - 若历史风险点中存在自杀相关风险且当前仍有依据，suicideRiskLevel不得低于历史等级
            - 若历史风险点中存在自伤相关风险且当前仍有依据，selfHarmRiskLevel不得低于历史等级
            - 若历史诊断摘要提示高风险，当前评估为低风险时必须在riskDetail中详细说明降级依据

            ## 注意事项
            - 风险评估宁可高估不可低估，涉及生命安全的问题必须审慎
            - 如果用户未明确表达自伤/自杀意念，但存在严重抑郁情绪且标准症状列表中有相关症状，selfHarmRiskLevel至少为1(低)
            - riskDetail应详细说明判断依据，引用具体的症状、情绪指标和历史风险点
            - 严禁将明确表达的自伤/自杀意念降级处理
            - 当输入数据不足以做出可靠判断时，应输出"无法判断"编码而非猜测，并将needManualIntervene设为1
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
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getOllamaModelName() != null ? config.getOllamaModelName() : defaultOllamaModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.2;
        double topP = config != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 30;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;

        return OllamaChatOptions.builder()
                .model(modelName).temperature(temperature).topK(topK).topP(topP).numPredict(maxToken)
                .repeatPenalty(1.2).frequencyPenalty(frequencyPenalty).presencePenalty(presencePenalty)
                .repeatLastN(50).numCtx(maxToken).numThread(Runtime.getRuntime().availableProcessors())
                .format(isJsonResponseFormat(config) ? "json" : null).truncate(true).seed(42).mirostat(2).mirostatTau(3.0f).mirostatEta(0.05f)
                .useMLock(false).useMMap(true).numGPU(-1).lowVRAM(false).build();
    }

    @Override
    protected ChatOptions buildDashScopeCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDashscopeModelName() != null ? config.getDashscopeModelName() : defaultDashscopeModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.2;
        double topP = config != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null ? config.getTopK() : 40;

        return DashScopeChatOptions.builder().model(modelName).temperature(temperature).topP(topP).topK(topK)
                .seed(42).maxToken(maxToken).repetitionPenalty(1.2)
                .responseFormat(DashScopeResponseFormat.builder().type(isJsonResponseFormat(config) ? DashScopeResponseFormat.Type.JSON_OBJECT : DashScopeResponseFormat.Type.TEXT).build())
                .enableThinking(true).thinkingBudget(5).enableSearch(false).stream(false)
                .incrementalOutput(false).multiModel(false).vlHighResolutionImages(false).build();
    }

    @Override
    protected ChatOptions buildDeepSeekCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDeepseekModelName() != null ? config.getDeepseekModelName() : defaultDeepseekModelName;
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.2;
        double topP = config != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;

        return DeepSeekChatOptions.builder().model(modelName).temperature(temperature).topP(topP).maxTokens(maxToken)
                .frequencyPenalty(frequencyPenalty).presencePenalty(presencePenalty)
                .responseFormat(ResponseFormat.builder().type(isJsonResponseFormat(config) ? ResponseFormat.Type.JSON_OBJECT : ResponseFormat.Type.TEXT).build())
                .logprobs(false).topLogprobs(null).build();
    }

    @Override
    protected ChatOptions buildDefaultCompanionOptions(AiNodeConfig config) {
        double temperature = config != null ? config.getTemperature().doubleValue() : 0.4;
        double topP = config != null ? config.getTopP().doubleValue() : 0.9;
        int maxToken = config != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.6;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.2;

        return ChatOptions.builder().topK(40).topP(topP).frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty).temperature(temperature).maxTokens(maxToken).build();
    }

    @Retryable(
            label = "risk-assessment-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config);
    }

    @Retryable(
            label = "risk-assessment-process-model",
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public RiskAssessmentResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config);
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