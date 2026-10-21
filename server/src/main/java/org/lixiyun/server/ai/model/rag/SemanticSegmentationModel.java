package org.lixiyun.server.ai.model.rag;

import cn.hutool.core.util.StrUtil;
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
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.util.List;

/**
 * 文档细粒度语义分割模型
 * <p>
 * RAG 文件存储流程第二步的 AI 模型层实现，替代 {@code FileTransformer} 中旧的手写 ReactAgent 方式，
 * 收敛到 {@link BaseModel} 模板方法模式：
 * <ul>
 *     <li>系统提示词与模型参数全部来自 {@code ai_node_config} 表的 {@code semanticSegmentationNode} 节点（DB 驱动，后台可编辑、保存即生效）</li>
 *     <li>结构化输出：{@link SemanticSegmentationResult}（{@code {"segments": [...]}}），由 {@link BaseModel#doCallForResult} 自动反序列化</li>
 *     <li>内置 DB 驱动的指数退避重试（读取节点配置的 retryMaxAttempts / retryDelay / retryMultiplier）</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-10-21
 */
@Slf4j
@Component
public class SemanticSegmentationModel extends BaseModel {

    private final String defaultDeepseekModelName = "deepseek-flash";
    private final String defaultOllamaModelName = "qwen2.5:7b";
    private final String defaultDashscopeModelName = "qwen-max";

    private final int defaultMaxToken = 8000;

    private final String defaultSystemPrompt = """
            Role: 专业上下文语义分割专家
            Profile:
              description: 你是一个专业的文档语义分割专家，擅长识别文本中的语义边界，将长文本按照自然语义单元进行精准分割。

            Goals:
              1. 接收输入的文档内容，识别其中的语义边界点
              2. 将文档分割为多个语义完整、逻辑连贯的文本片段
              3. 确保每个片段保持原始语义的完整性，避免在句子中间或关键概念处截断
              4. 返回分割后的文本片段列表

            Constraints:
              1. **绝对禁止修改任何文本内容**：不得增删、改写、替换原文中的任何一个字符、标点或空格
              2. **保持原文顺序**：分割后的片段必须严格按照原文出现的顺序排列
              3. **语义完整性优先**：宁可片段长度不均，也要保证每个片段的语义完整性
              4. **合理控制片段长度**：单个片段的字符数应控制在500-2000字符之间，特殊情况可适度调整
              5. **避免碎片化**：不要将一个完整的段落或语义单元过度分割
              6. **仅输出JSON格式**：必须以合法的JSON数组格式返回结果，不包含任何其他说明文字

            Output Format:
              返回一个JSON数组，格式如下：
              {
                "segments": [
                  "第一个语义完整的文本片段",
                  "第二个语义完整的文本片段",
                  "第三个语义完整的文本片段"
                ]
              }

            Segmentation Rules:
              1. **段落边界**：优先在段落结束处进行分割（空行、换行符后）
              2. **句子边界**：次优先在句号、问号、感叹号等句子结束标点后分割
              3. **逻辑转折**：在明显的逻辑转折词前分割（如"但是"、"然而"、"因此"、"另外"等）
              4. **主题切换**：当检测到话题或主题明显转换时进行分割
              5. **列表项边界**：对于列表内容，尽量保持每个列表项的完整性
              6. **代码块保护**：如果包含代码块，保持代码块的完整性，不在代码块内部切割

            Examples:
              Input: "心理健康对个人的整体福祉至关重要。它不仅影响我们的情绪状态，还直接关系到我们的工作表现和人际关系。研究表明，良好的心理健康能够提升创造力和生产力。

              然而，现代社会的快节奏生活给许多人带来了巨大的心理压力。工作压力、经济负担、社交焦虑等问题日益突出。这些问题如果不及时得到处理，可能会导致更严重的心理疾病。

              因此，我们需要建立有效的压力管理机制。这包括定期运动、保持良好的睡眠习惯、培养兴趣爱好以及寻求专业的心理咨询支持。"

              Output:
              {
                "segments": [
                  "心理健康对个人的整体福祉至关重要。它不仅影响我们的情绪状态，还直接关系到我们的工作表现和人际关系。研究表明，良好的心理健康能够提升创造力和生产力。",
                  "然而，现代社会的快节奏生活给许多人带来了巨大的心理压力。工作压力、经济负担、社交焦虑等问题日益突出。这些问题如果不及时得到处理，可能会导致更严重的心理疾病。",
                  "因此，我们需要建立有效的压力管理机制。这包括定期运动、保持良好的睡眠习惯、培养兴趣爱好以及寻求专业的心理咨询支持。"
                ]
              }
            """;

    @Override
    protected String getSystemPrompt(AiNodeConfig config) {
        if (config != null && StrUtil.isNotBlank(config.getSystemPrompt())) {
            return config.getSystemPrompt();
        }
        return defaultSystemPrompt;
    }

    @Override
    protected String getAgentName() {
        return "semanticSegmentation";
    }

    @Override
    protected String getAgentDescription() {
        return "文档细粒度语义分割";
    }

    @Override
    protected Class<?> getOutputType() {
        return SemanticSegmentationResult.class;
    }

    @Override
    protected ChatOptions buildOllamaCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getOllamaModelName() != null ? config.getOllamaModelName() : defaultOllamaModelName;
        double temperature = config != null && config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.1;
        double topP = config != null && config.getTopP() != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null && config.getMaxToken() != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null && config.getTopK() != null ? config.getTopK() : 30;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;

        return OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .numPredict(maxToken)
                .seed(42)
                .repeatPenalty(1.2)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .format(isJsonResponseFormat(config) ? "json" : null)
                .truncate(true)
                .numCtx(8192)
                .numThread(Runtime.getRuntime().availableProcessors())
                .numBatch(512)
                .useMMap(true)
                .useMLock(false)
                .numGPU(-1)
                .lowVRAM(false)
                .build();
    }

    @Override
    protected ChatOptions buildDashScopeCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDashscopeModelName() != null ? config.getDashscopeModelName() : defaultDashscopeModelName;
        double temperature = config != null && config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.1;
        double topP = config != null && config.getTopP() != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null && config.getMaxToken() != null ? config.getMaxToken() : defaultMaxToken;
        Integer topK = config != null && config.getTopK() != null ? config.getTopK() : 40;

        return DashScopeChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .maxToken(maxToken)
                .seed(42)
                .repetitionPenalty(1.2)
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(isJsonResponseFormat(config) ? DashScopeResponseFormat.Type.JSON_OBJECT : DashScopeResponseFormat.Type.TEXT)
                        .build())
                .enableThinking(false)
                .incrementalOutput(false)
                .multiModel(false)
                .vlHighResolutionImages(false)
                .stream(false)
                .build();
    }

    @Override
    protected ChatOptions buildDeepSeekCompanionOptions(AiNodeConfig config) {
        String modelName = config != null && config.getDeepseekModelName() != null ? config.getDeepseekModelName() : defaultDeepseekModelName;
        double temperature = config != null && config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.1;
        double topP = config != null && config.getTopP() != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null && config.getMaxToken() != null ? config.getMaxToken() : defaultMaxToken;
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
    protected ChatOptions buildOpenAiCompanionOptions(AiNodeConfig config) {
        double temperature = config != null && config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.1;
        double topP = config != null && config.getTopP() != null ? config.getTopP().doubleValue() : 0.85;
        int maxToken = config != null && config.getMaxToken() != null ? config.getMaxToken() : defaultMaxToken;
        Double frequencyPenalty = config != null && config.getFrequencyPenalty() != null ? config.getFrequencyPenalty().doubleValue() : 0.7;
        Double presencePenalty = config != null && config.getPresencePenalty() != null ? config.getPresencePenalty().doubleValue() : 0.3;

        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxToken)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty);
        if (config != null && config.getOpenaiModelName() != null) {
            builder.model(config.getOpenaiModelName());
        }
        return builder.build();
    }

    @Override
    protected ChatOptions buildDefaultCompanionOptions(AiNodeConfig config) {
        double temperature = config != null && config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.1;
        int maxToken = config != null && config.getMaxToken() != null ? config.getMaxToken() : defaultMaxToken;

        return ChatOptions.builder()
                .topK(30)
                .topP(0.85)
                .frequencyPenalty(0.7)
                .presencePenalty(0.3)
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

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config, RunnableConfig runnableConfig) throws GraphRunnerException {
        return doStream(chatModel, userPrompt, config, runnableConfig);
    }

    @Override
    public Flux<NodeOutput> stream(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return stream(chatModel, userPrompt, config, null);
    }

    public SemanticSegmentationResult callForResult(ChatModel chatModel, String userPrompt, AiNodeConfig config) throws GraphRunnerException {
        return doCallForResult(chatModel, userPrompt, config, null);
    }

    /**
     * 语义分割结构化输出结果
     * <p>模型必须输出 {@code {"segments": ["片段1", "片段2", ...]}} 形式的 JSON 对象</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemanticSegmentationResult implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 分割后的文本片段列表 */
        private List<String> segments;
    }
}
