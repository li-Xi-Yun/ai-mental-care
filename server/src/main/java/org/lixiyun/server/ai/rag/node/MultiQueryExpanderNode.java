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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 预检索组件：用于将一个检索语句变体为多个检索语句
 * <p>该节点接收压缩查询和重写查询的结果，通过LLM模型将其扩展为多个不同角度的检索语句，提升RAG检索的召回率</p>
 * @author lixiyun
 * @since 2026-04-27 09:11
 */
@Slf4j
@Builder
public class MultiQueryExpanderNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "multiQueryExpanderNode";

    private final String expansionPromptTemplate = PromptUtil.getPrompt(RagConstant.MULTI_QUERY_EXPANSION);

    private final ChatModel chatModel;

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("queryExpansion")
                .description("查询扩展")
                .chatOptions(chatOptions())
                .enableLogging(false);
    }

    private ChatOptions chatOptions() {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        if (chatModel instanceof OllamaChatModel) {
            return OllamaChatOptions.builder()
                    .temperature(0.5)          // 中等随机性，保证查询多样性的同时保持相关性
                    .topK(40)                  // 扩大候选词范围，增加表达多样性
                    .topP(0.9)                 // 聚焦高概率词汇，保证查询质量
                    .numPredict(500)           // 足够容纳多个扩展查询
                    .seed(42)                  // 固定随机种子，保证结果一致性

                    .repeatPenalty(1.3)        // 强化重复惩罚，避免生成相似查询
                    .frequencyPenalty(0.8)     // 降低高频词重复
                    .presencePenalty(0.5)      // 适度惩罚已出现的主题，鼓励多样性
                    .repeatLastN(80)           // 监控最近80个token

                    .truncate(true)            // 自动截断超长输入

                    .numCtx(2048)              // 上下文窗口，容纳查询+提示词
                    .numThread(Runtime.getRuntime().availableProcessors())
                    .numBatch(512)

                    .useMMap(true)
                    .useMLock(false)
                    .numGPU(-1)
                    .lowVRAM(false)

                    .mirostat(2)
                    .mirostatTau(3.5f)
                    .mirostatEta(0.05f)
                    .build();

        } else if (chatModel instanceof DashScopeChatModel) {
            return DashScopeChatOptions.builder()
                    .temperature(0.5)          // 中等随机性，平衡多样性与相关性
                    .topP(0.9)                 // 聚焦高概率词汇
                    .topK(50)                  // 扩大候选词范围
                    .seed(42)                  // 固定随机种子
                    .maxToken(600)             // 足够容纳多个扩展查询

                    .repetitionPenalty(1.2)    // 惩罚重复token

                    .responseFormat(DashScopeResponseFormat.builder()
                            .type(DashScopeResponseFormat.Type.TEXT)
                            .build())

                    .enableThinking(true)      // 启用思考模式，深入理解查询扩展需求
                    .thinkingBudget(5)         // 思考预算，平衡多样性与响应速度
                    .enableSearch(false)       // 无需联网搜索
                    .stream(false)             // 非流式输出
                    .incrementalOutput(false)
                    .multiModel(false)
                    .vlHighResolutionImages(false)
                    .build();

        } else if (chatModel instanceof DeepSeekChatModel) {
            return DeepSeekChatOptions.builder()
                    .temperature(0.5)          // 中等随机性
                    .topP(0.9)                 // 聚焦高概率词汇
                    .maxTokens(600)            // 足够容纳多个扩展查询

                    .frequencyPenalty(0.8)     // 惩罚高频重复
                    .presencePenalty(0.5)      // 适度惩罚已出现主题

                    .responseFormat(ResponseFormat.builder()
                            .type(ResponseFormat.Type.TEXT)
                            .build())

                    .logprobs(false)
                    .topLogprobs(null)
                    .build();
        } else {
            return ChatOptions.builder()
                    .topK(50)
                    .topP(0.9)
                    .frequencyPenalty(0.7)
                    .presencePenalty(0.4)
                    .temperature(0.6)
                    .maxTokens(600)
                    .build();
        }
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.debug("多查询扩展节点开始执行");

        // 获取压缩查询结果
        Optional<Object> compressedQueryOpl = state.value(CompressionQueryTransformerNode.NODE_NAME);
        String compressedQuery = compressedQueryOpl
                .map(Object::toString)
                .orElse("");

        // 获取重写查询结果
        Optional<Object> rewrittenQueryOpl = state.value(RewriteQueryTransformerNode.NODE_NAME);
        String rewrittenQuery = rewrittenQueryOpl
                .map(Object::toString)
                .orElse("");

        log.debug("多查询扩展节点：压缩查询={}", compressedQuery);
        log.debug("多查询扩展节点：重写查询={}", rewrittenQuery);

        // 如果两个查询都为空，直接返回空结果
        if (compressedQuery.isBlank() && rewrittenQuery.isBlank()) {
            log.warn("多查询扩展节点：压缩查询和重写查询均为空，无法进行扩展");
            // 交由下一个节点处理
            return Map.of();
        }

        // 构建输入：合并两个查询结果
        String prompt = String.format(expansionPromptTemplate, compressedQuery, rewrittenQuery);

        // 调用模型进行查询扩展
        AssistantMessage call = reactAgentBuilder()
                .systemPrompt(prompt)
                .build()
                .call("");

        String modelOutput = call.getText();
        log.info("多查询扩展节点：模型输出结果={}", modelOutput);

        // 清理模型输出
        if (modelOutput == null || modelOutput.trim().isEmpty()) {
            log.warn("多查询扩展节点：模型未返回有效扩展结果");
            return Map.of();
        }

        // 清理输出：去除首尾空白
        String expandedQueries = modelOutput.trim();
        
        // 验证输出格式：应该包含双竖线分隔符
        if (!expandedQueries.contains("||")) {
            log.warn("多查询扩展节点：模型输出格式不正确，未找到分隔符||");
            return Map.of();
        }

        // 使用Stream处理：分割、清理、过滤空字符串
        List<String> queryList = Arrays.stream(expandedQueries.split("\\|\\|"))
                .map(String::trim)                          // 去除每个查询的首尾空白
                .filter(query -> !query.isEmpty())          // 过滤空字符串
                .collect(Collectors.toList());

        log.debug("多查询扩展节点：扩展后查询数量={}, 查询列表={}", queryList.size(), queryList);

        // 将扩展后的查询列表存入state，供后续RetrieverNode使用
        return Map.of(NODE_NAME, queryList);
    }
}
