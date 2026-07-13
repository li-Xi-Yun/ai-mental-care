package org.lixiyun.server.ai.rag.node;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.prompt.constant.RagConstant;
import org.lixiyun.common.agent.prompt.utils.PromptUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 预检索组件：用于将用户查询压缩为多个核心词语
 * <p>该节点通过LLM模型将用户的自然语言查询转换为适合向量检索的核心关键词组合，提升RAG检索的准确性</p>
 * @author lixiyun
 * @since 2026-04-27 09:00
 */
@Slf4j
@Builder
public class CompressionQueryTransformerNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "compressionQueryTransformerNode";

    private final String compressionPrompt = PromptUtil.getPrompt(RagConstant.COMPRESSION_QUERY);

    private final ChatModel chatModel;

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("queryCompression")
                .description("查询压缩")
                .chatOptions(chatOptions())
                .enableLogging(false);
    }

    private ChatOptions chatOptions() {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        if (chatModel instanceof OllamaChatModel) {
            return OllamaChatOptions.builder()
                    .temperature(0.1)          // 极低随机性，确保相同查询生成一致的关键词
                    .topK(20)                  // 限制候选词范围，聚焦核心关键词
                    .topP(0.85)                // 聚焦高概率词汇，避免无意义关键词
                    .numPredict(200)           // 足够容纳JSON输出（关键词数组约100-150token）
                    .seed(42)                  // 固定随机种子，保证结果一致性

                    .repeatPenalty(1.3)        // 强化重复惩罚，避免关键词重复
                    .frequencyPenalty(0.8)     // 降低高频词重复
                    .presencePenalty(0.4)      // 轻微惩罚已出现的主题
                    .repeatLastN(50)           // 监控最近50个token

                    .format("json")            // 强制JSON格式输出
                    .truncate(true)            // 自动截断超长输入

                    .numCtx(2048)              // 上下文窗口，容纳查询+提示词
                    .numThread(Runtime.getRuntime().availableProcessors())
                    .numBatch(512)

                    .useMMap(true)
                    .useMLock(false)
                    .numGPU(-1)
                    .lowVRAM(false)

                    .mirostat(2)
                    .mirostatTau(2.5f)
                    .mirostatEta(0.05f)
                    .build();

        } else if (chatModel instanceof DashScopeChatModel) {
            return DashScopeChatOptions.builder()
                    .temperature(0.1)          // 极低随机性，确保关键词提取稳定
                    .topP(0.85)                // 聚焦高概率词汇
                    .topK(30)                  // 限制候选词范围
                    .seed(42)                  // 固定随机种子
                    .maxToken(300)             // 足够容纳JSON输出

                    .repetitionPenalty(1.2)    // 惩罚重复token

                    .responseFormat(DashScopeResponseFormat.builder()
                            .type(DashScopeResponseFormat.Type.TEXT)
                            .build())

                    .enableThinking(false)     // 关键词提取无需深度思考，禁用以提升速度
                    .enableSearch(false)       // 无需联网搜索
                    .stream(false)             // 非流式输出
                    .incrementalOutput(false)
                    .multiModel(false)
                    .vlHighResolutionImages(false)
                    .build();

        } else if (chatModel instanceof DeepSeekChatModel) {
            return DeepSeekChatOptions.builder()
                    .temperature(0.1)          // 极低随机性
                    .topP(0.85)                // 聚焦高概率词汇
                    .maxTokens(300)            // 足够容纳JSON输出

                    .frequencyPenalty(0.8)     // 惩罚高频重复
                    .presencePenalty(0.4)      // 轻微惩罚已出现主题

                    .responseFormat(ResponseFormat.builder()
                            .type(ResponseFormat.Type.TEXT)
                            .build())

                    .logprobs(false)
                    .topLogprobs(null)
                    .build();
        } else {
            return ChatOptions.builder()
                    .topK(30)
                    .topP(0.85)
                    .frequencyPenalty(0.7)
                    .presencePenalty(0.3)
                    .temperature(0.2)
                    .maxTokens(300)
                    .build();
        }
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.debug("查询压缩节点开始执行");

        // 获取历史消息上下文
        Optional<List<Message>> messagesOpl = state.value(GraphConstant.MESSAGES);
        List<Message> historyMessages = messagesOpl.orElseThrow(() -> {
            log.error("查询压缩节点-historyMessages:历史消息不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR);
        });
        log.debug("查询压缩节点：历史消息数量={}", historyMessages.size());

        // 获取用户输入
        Optional<String> inputOpl = state.value(GraphConstant.INPUT);
        String userInput = inputOpl.orElseThrow(() -> {
            log.error("查询压缩节点-input:用户输入不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR);
        });

        String prompt = compressionPrompt + "\n用户输入：" + userInput;

        // 调用模型进行查询压缩
        AssistantMessage call = reactAgentBuilder()
                .systemPrompt(prompt)
                .build()
                .call(historyMessages);

        String modelOutput = call.getText();
        log.info("查询压缩节点：模型输出结果={}", modelOutput);

        // 清理模型输出，去除可能的多余空白和换行
        if (modelOutput == null || modelOutput.trim().isEmpty()) {
            log.warn("查询压缩节点：模型未返回有效关键词，使用原始查询");
            return Map.of();
        }

        // 清理输出：去除首尾空白、多余空格和换行符
        String compressedQueryStr = modelOutput.trim()
                .replaceAll("\\s+", " ")  // 将多个空白字符替换为单个空格
                .replaceAll("[\"'\\[\\]{}]", "");  // 去除可能的JSON符号
        
        log.debug("查询压缩节点：压缩后查询={}", compressedQueryStr);

        // 将压缩后的查询存入state，供后续 MultiQueryExpanderNode 使用
        return Map.of(NODE_NAME, compressedQueryStr);
    }
}
