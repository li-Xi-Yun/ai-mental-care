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
 * 预检索组件：用于将用户查询变体为专业领域的术语
 * <p>该节点通过LLM模型将用户的自然语言查询重写为心理健康领域的专业术语表达，提升RAG检索的准确性和专业性</p>
 * @author lixiyun
 * @since 2026-04-27 09:02
 */
@Slf4j
@Builder
public class RewriteQueryTransformerNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "rewriteQueryTransformerNode";

    private final String rewritePrompt = PromptUtil.getPrompt(RagConstant.REWRITE_QUERY);

    private final ChatModel chatModel;

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("queryRewrite")
                .description("查询重写")
                .chatOptions(chatOptions())
                .enableLogging(false);
    }

    private ChatOptions chatOptions() {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        if (chatModel instanceof OllamaChatModel) {
            return OllamaChatOptions.builder()
                    .temperature(0.3)          // 较低随机性，保证术语转换的专业性和一致性
                    .topK(30)                  // 限制候选词范围，聚焦专业术语
                    .topP(0.9)                 // 聚焦高概率词汇，保证术语准确性
                    .numPredict(300)           // 足够容纳重写后的查询文本
                    .seed(42)                  // 固定随机种子，保证结果一致性

                    .repeatPenalty(1.2)        // 惩罚重复token，避免术语重复
                    .frequencyPenalty(0.7)     // 降低高频词重复
                    .presencePenalty(0.3)      // 轻微惩罚已出现的主题
                    .repeatLastN(60)           // 监控最近60个token

                    .truncate(true)            // 自动截断超长输入

                    .numCtx(2048)              // 上下文窗口，容纳查询+提示词
                    .numThread(Runtime.getRuntime().availableProcessors())
                    .numBatch(512)

                    .useMMap(true)
                    .useMLock(false)
                    .numGPU(-1)
                    .lowVRAM(false)

                    .mirostat(2)
                    .mirostatTau(3.0f)
                    .mirostatEta(0.05f)
                    .build();

        } else if (chatModel instanceof DashScopeChatModel) {
            return DashScopeChatOptions.builder()
                    .temperature(0.3)          // 较低随机性，保证术语转换稳定性
                    .topP(0.9)                 // 聚焦高概率词汇
                    .topK(40)                  // 限制候选词范围
                    .seed(42)                  // 固定随机种子
                    .maxToken(400)             // 足够容纳重写后的查询文本

                    .repetitionPenalty(1.15)   // 惩罚重复token

                    .responseFormat(DashScopeResponseFormat.builder()
                            .type(DashScopeResponseFormat.Type.TEXT)
                            .build())

                    .enableThinking(true)      // 启用思考模式，深入理解心理学语境
                    .thinkingBudget(5)         // 思考预算，平衡专业性与响应速度
                    .enableSearch(false)       // 无需联网搜索
                    .stream(false)             // 非流式输出
                    .incrementalOutput(false)
                    .multiModel(false)
                    .vlHighResolutionImages(false)
                    .build();

        } else if (chatModel instanceof DeepSeekChatModel) {
            return DeepSeekChatOptions.builder()
                    .temperature(0.3)          // 较低随机性
                    .topP(0.9)                 // 聚焦高概率词汇
                    .maxTokens(400)            // 足够容纳重写后的查询文本

                    .frequencyPenalty(0.7)     // 惩罚高频重复
                    .presencePenalty(0.3)      // 轻微惩罚已出现主题

                    .responseFormat(ResponseFormat.builder()
                            .type(ResponseFormat.Type.TEXT)
                            .build())

                    .logprobs(false)
                    .topLogprobs(null)
                    .build();
        } else {
            return ChatOptions.builder()
                    .topK(40)
                    .topP(0.9)
                    .frequencyPenalty(0.6)
                    .presencePenalty(0.2)
                    .temperature(0.4)
                    .maxTokens(400)
                    .build();
        }
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.debug("查询重写节点开始执行");

        // 获取历史消息上下文
        Optional<List<Message>> messagesOpl = state.value(GraphConstant.MESSAGES);
        List<Message> historyMessages = messagesOpl.orElseThrow(() -> {
            log.error("查询重写节点-historyMessages:历史消息不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR);
        });
        log.debug("查询重写节点：历史消息数量={}", historyMessages.size());

        // 获取用户输入
        Optional<String> inputOpl = state.value(GraphConstant.INPUT);
        String userInput = inputOpl.orElseThrow(() -> {
            log.error("查询重写节点-input:用户输入不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_PARAM_ERROR);
        });
        String prompt = rewritePrompt + "\n用户输入：" + userInput;

        // 调用模型进行查询重写
        AssistantMessage call = reactAgentBuilder()
                .systemPrompt(prompt)
                .build()
                .call(historyMessages);

        String modelOutput = call.getText();
        log.info("查询重写节点：模型输出结果={}", modelOutput);

        // 清理模型输出，去除可能的多余空白和换行
        if (modelOutput == null || modelOutput.trim().isEmpty()) {
            log.warn("查询重写节点：模型未返回有效重写结果，使用原始查询");
            return Map.of();
        }

        // 清理输出：去除首尾空白、多余空格和换行符
        String rewrittenQuery = modelOutput.trim()
                .replaceAll("\\s+", " ");  // 将多个空白字符替换为单个空格
        
        log.debug("查询重写节点：重写后查询={}", rewrittenQuery);

        // 将重写后的查询存入state，供后续RetrieverNode使用
        return Map.of(NODE_NAME, rewrittenQuery);
    }
}
