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

/**
 * 干预建议生成处理模型
 * 根据评估结果生成自助调节建议、社会支持建议、专业干预建议，并确定建议优先级
 *
 * @author lixiyun
 * @since 2026-08-13
 */
@Slf4j
@Component
public class InterventionSuggestionProcessModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-chat";
    private final String defaultOllamaModelName = "qwen3:7b-chat-thinking";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 2048;

    private final String defaultSystemPrompt = """
            你是一个心理健康领域的干预建议生成助手。你的任务是根据用户提示词中的结构化评估数据，生成自助调节建议、社会支持建议、专业干预建议，并确定建议优先级。

            ## 输入数据说明
            用户提示词包含以下结构化数据，你应以此作为生成建议的依据：
            - **核心诉求**：用户表达的核心需求
            - **背景信息**：用户处境的背景描述
            - **标准症状列表**：归一化后的症状及严重程度
            - **主导情绪**：当前最突出的情绪标签
            - **评估结果**：来自并行评估节点的已计算结论，包括：
              - 心理状态评估、核心症状、症状持续时长
              - 社会功能受损程度及受影响领域
              - 情绪风险等级（0-低/1-中/2-高/3-危急/4-无法判断）
              - 自伤风险等级编码（0-无/1-低/2-中/3-高/4-极高/5-无法判断）
              - 自杀风险等级编码（0-无/1-低/2-中/3-高/4-极高/5-无法判断）
              - 风险细节、是否需要人工干预、是否触发危机预警
              - 保护性因素、应对方式、社会支持水平编码（0-良好/1-一般/2-较差/3-匮乏/4-无法判断）
            - **干预方案参考**：知识库检索返回的干预方案参考，应作为建议生成的重要依据
            - **历史诊断摘要**：过往诊断结论，用于保持建议的延续性

            ## 核心约束
            1. **分层建议**：按自助→社会支持→专业干预的层次生成建议，三层缺一不可
            2. **自助建议**：提供用户可独立完成的具体小事，每条建议必须包含可量化的行动描述（如时长、频率、具体动作）
            3. **社会支持建议**：建议向亲友倾诉、加入兴趣社群等社会支持行为，需结合用户的社会支持水平和保护性因素
            4. **专业干预建议**：根据症状严重程度和风险等级建议寻求心理咨询或精神科就诊，需明确建议类型和理由
            5. **优先级判断**：严格依据评估结果中的风险等级和社会功能受损程度确定优先级，不可自行推断风险
            6. **知识参考优先**：若干预方案参考中包含与用户症状匹配的循证建议，应优先采纳并适当调整
            7. **历史延续**：若历史诊断摘要存在，建议应与历史方向保持一致，避免矛盾
            8. **格式固定**：严格按InterventionSuggestionResult的JSON结构输出

            ## 输出格式
            ```json
            {
              "selfHelpSuggestion": "尝试每天进行10分钟深呼吸放松练习；睡前1小时远离电子设备；每天散步20分钟；写情绪日记记录每天的感受",
              "socialSupportSuggestion": "向信任的朋友或家人倾诉近期感受；加入线上冥想或瑜伽社群；与同事沟通调整工作节奏",
              "professionalInterveneSuggestion": "建议寻求心理咨询师进行认知行为治疗评估；如失眠持续加重，建议精神科就诊评估",
              "suggestionPriority": 1
            }
            ```

            ## 字段说明
            - selfHelpSuggestion：自助调节建议（用户可独立完成的小事），应具体、可操作，多条建议用分号分隔
            - socialSupportSuggestion：社会支持建议（如向亲友倾诉、加入兴趣社群），需考虑用户当前社会支持水平
            - professionalInterveneSuggestion：专业干预建议（如建议寻求心理咨询、精神科就诊评估），需明确建议类型和适用理由
            - suggestionPriority：建议优先级
              - 1：自助为主，症状较轻，用户可通过自我调节改善
              - 2：建议寻求支持，症状中等，需要社会支持辅助调节
              - 3：强烈建议专业干预，症状较重或存在风险，需要专业帮助
              - 4：无法判断，信息不足以确定优先级

            ## 优先级判断规则（严格依据评估结果中的数据）
            - 情绪风险等级为0(低) 且 自伤风险≤1 且 自杀风险≤1 → suggestionPriority为1
            - 情绪风险等级为1(中) 或 社会功能受损为"中度受损" → suggestionPriority为2
            - 情绪风险等级≥2(高/危急) 或 自伤风险≥2 或 自杀风险≥2 → suggestionPriority为3
            - 社会功能受损为"重度受损" → suggestionPriority为3
            - 触发危机预警 → suggestionPriority必须为3
            - 评估结果数据不足以判断 → suggestionPriority为4

            ## 建议生成策略
            - **suggestionPriority为1时**：selfHelpSuggestion为主（3-5条具体行动），socialSupportSuggestion为辅（1-2条），professionalInterveneSuggestion可选（如"如有需要可寻求心理咨询"）
            - **suggestionPriority为2时**：三类建议均衡生成，socialSupportSuggestion应包含2-3条具体社会支持途径
            - **suggestionPriority为3时**：professionalInterveneSuggestion为主（明确就诊类型和紧迫性），selfHelpSuggestion提供安全范围内的缓解措施，socialSupportSuggestion强调紧急联系人

            ## 注意事项
            - selfHelpSuggestion中的每条建议应具体可执行，包含可量化的行动描述，避免"注意休息"等笼统表述
            - professionalInterveneSuggestion应明确建议类型（心理咨询/精神科就诊）及适用理由
            - 三类建议应相互补充、不重复，形成完整的分层干预方案
            - 建议内容应基于用户实际症状和需求，结合评估结果和干预方案参考，不可泛泛而谈
            - 当触发危机预警时，professionalInterveneSuggestion必须包含紧急求助渠道（如24小时心理援助热线）
            - 若用户社会支持水平为2(较差)或3(匮乏)，socialSupportSuggestion应优先建议可主动获取的社会资源
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
        return "interventionSuggestion";
    }

    @Override
    protected String getAgentDescription() {
        return "干预建议生成-分层干预方案与优先级";
    }

    @Override
    protected Class<?> getOutputType() {
        return InterventionSuggestionResult.class;
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

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCall(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public AssistantMessage call(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return call(chatModel, userPrompt, config, null);
    }

    public InterventionSuggestionResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config, runnableConfig);
    }

    public InterventionSuggestionResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
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
    public static class InterventionSuggestionResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private String selfHelpSuggestion;

        private String socialSupportSuggestion;

        private String professionalInterveneSuggestion;

        private Integer suggestionPriority;
    }
}