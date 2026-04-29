package org.lixiyun.server.ai.node.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索后置处理组件：用于将从向量数据库中的存储数据进行后续打分，只取其中前几个数据
 * <p>该节点接收向量检索结果，先获取Top-K最匹配文档，然后使用LLM模型对文档进行相关性重排序和筛选，提升RAG检索的精准度</p>
 * @author lixiyun
 * @since 2026-04-27 09:16
 */
@Slf4j
@Builder
public class RerankerNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "rerankerNode";

    private static final int INITIAL_TOP_K = 10;     // 初始从检索结果中选取的Top-K文档数量
    private static final int FINAL_TOP_K = 5;        // 最终返回的Top-K文档数量
    private static final double RELEVANCE_THRESHOLD = 0.7;  // 相关性阈值，低于此值的文档将被过滤
    private static final int RETRY_COUNT = 3;

    private final String rerankerPrompt = """
            Role: 文档相关性评估专家
            Profile:
              description: 你是一名专业的文档相关性评估专家，擅长通过语义分析判断文档内容与用户提问的关联度。
            Goals:
              1. 深入理解用户查询的核心意图和语义需求
              2. 通过语义分析评估每个文档内容与用户查询的关联程度
              3. 只返回关联度高的文档ID（评分>=0.7）
              4. 按关联度从高到低排序，最多返回3个最相关的文档ID
            Constraints:
              1. 仅输出JSON格式，不包含任何额外说明、注释或解释
              2. 关联性评分必须为0-1之间的小数，保留两位小数
              3. 严格筛选：只返回关联性评分>=0.7的文档，低于此阈值的文档不要返回
              4. 最多返回3个关联度最高的文档ID
              5. 若所有文档都与用户查询关联度低，返回空数组
              6. 只需返回文档ID和关联性评分，不需要返回文档内容或其他信息
            EvaluationCriteria:
              - 高关联(0.85-1.0): 文档内容直接回答用户问题或高度相关
              - 中关联(0.7-0.84): 文档内容与用户问题有部分相关性
              - 低关联(<0.7): 文档内容与用户问题关联度低，不应返回
            OutputFormat:
              {
                "rankedDocuments": [
                  {"docId": "文档ID", "relevanceScore": 0.95},
                  {"docId": "文档ID", "relevanceScore": 0.82}
                ]
              }
            Examples:
              User Query: "如何缓解考试焦虑？"
              Documents: [doc1(考试内容), doc2(失眠治疗), doc3(焦虑管理)]
              Assistant: {"rankedDocuments": [{"docId": "doc3", "relevanceScore": 0.92}, {"docId": "doc1", "relevanceScore": 0.75}]}
              
              User Query: "工作压力大怎么办"
              Documents: [doc1(娱乐新闻), doc2(职场压力), doc3(饮食健康)]
              Assistant: {"rankedDocuments": [{"docId": "doc2", "relevanceScore": 0.88}]}
            
            以下是相关文档内容：
            %s
            """;

    private final ChatModel chatModel;

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuilder() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("documentReranker")
                .description("文档重排序")
                .chatOptions(chatOptions())
                .enableLogging(false);
    }

    private ChatOptions chatOptions() {
        if (chatModel == null) {
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        if (chatModel instanceof OllamaChatModel) {
            return OllamaChatOptions.builder()
                    .temperature(0.1)          // 极低随机性，保证评分的一致性
                    .topK(20)                  // 限制候选词范围
                    .topP(0.85)                // 聚焦高概率词汇
                    .numPredict(2000)          // 足够容纳JSON输出（多个文档的评分）
                    .seed(42)                  // 固定随机种子

                    .repeatPenalty(1.2)        // 惩罚重复token
                    .frequencyPenalty(0.7)     // 降低高频词重复
                    .presencePenalty(0.3)      // 轻微惩罚已出现的主题
                    .repeatLastN(100)          // 监控最近100个token

                    .format("json")            // 强制JSON格式输出
                    .truncate(true)            // 自动截断超长输入

                    .numCtx(4096)              // 扩大上下文窗口，容纳多个文档内容
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
                    .temperature(0.1)          // 极低随机性，保证评分稳定性
                    .topP(0.85)                // 聚焦高概率词汇
                    .topK(30)                  // 限制候选词范围
                    .seed(42)                  // 固定随机种子
                    .maxToken(3000)            // 足够容纳JSON输出

                    .repetitionPenalty(1.2)    // 惩罚重复token

                    .responseFormat(DashScopeResponseFormat.builder()
                            .type(DashScopeResponseFormat.Type.JSON_OBJECT)
                            .build())

                    .enableThinking(true)      // 启用思考模式，深入理解文档相关性
                    .thinkingBudget(5)         // 思考预算，平衡准确性与响应速度
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
                    .maxTokens(3000)           // 足够容纳JSON输出

                    .frequencyPenalty(0.7)     // 惩罚高频重复
                    .presencePenalty(0.3)      // 轻微惩罚已出现主题

                    .responseFormat(ResponseFormat.builder()
                            .type(ResponseFormat.Type.JSON_OBJECT)
                            .build())

                    .logprobs(false)
                    .topLogprobs(null)
                    .build();
        } else {
            return ChatOptions.builder()
                    .topK(30)
                    .topP(0.85)
                    .frequencyPenalty(0.6)
                    .presencePenalty(0.2)
                    .temperature(0.2)
                    .maxTokens(3000)
                    .build();
        }
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.debug("文档重排序节点开始执行");

        // 获取向量检索结果（Map<String, Document>格式）
        Optional<Map<String, Document>> retrievedDocsOpl = state.value(RetrieverNode.NODE_NAME);
        Map<String, Document> retrievedDocuments = retrievedDocsOpl.orElse(Map.of());

        log.debug("文档重排序节点：检索到的文档数量={}", retrievedDocuments.size());

        if (retrievedDocuments.isEmpty()) {
            log.warn("文档重排序节点：检索结果为空，无法进行重排序");
            config.context().put(GraphConstant.RAG_INTERRUPTED, true);
            return Map.of();
        }

        // 步骤1：从检索结果中选取Top-K个文档（基于向量相似度）
        Map<String, String> topKDocuments = retrievedDocuments.values().stream()
                .sorted(Comparator.comparingDouble(item -> {
                    if(item instanceof Document doc){
                        return Objects.requireNonNullElse(doc.getScore(), 0.0);
                    }
                    return 0.0;
                }).reversed())
                .limit(INITIAL_TOP_K)
                .collect(Collectors.
                        toMap(Document::getId,
                                Document::getText,
                                (oldValue, newValue) -> oldValue)
                );

        log.debug("文档重排序节点：选取Top-K文档数量={}", topKDocuments.size());

        if (topKDocuments.isEmpty()) {
            log.warn("文档重排序节点：Top-K文档为空");
            return Map.of();
        }

        // 获取历史消息上下文
        Optional<List<Message>> messagesOpl = state.value(GraphConstant.MESSAGES);
        List<Message> historyMessages = messagesOpl.orElse(List.of());

        // 将Map转换为JSON字符串形式
        String documentsJson = JsonUtils.toJsonString(topKDocuments);
        log.debug("文档重排序节点：文档Map JSON={}", documentsJson);
        String rerankerPromptAgent = String.format(rerankerPrompt, documentsJson);

        // 步骤3：调用模型进行相关性评估和重排序
        AssistantMessage call = reactAgentBuilder()
                .systemPrompt(rerankerPromptAgent)
                .outputType(RerankedResult.class)
                .build()
                .call(historyMessages);

        String modelOutput = call.getText();
        log.info("文档重排序节点：模型输出结果={}", modelOutput);

        // 步骤4：解析模型输出
        RerankedResult rerankedResult = JsonUtils.parseObject(modelOutput, RerankedResult.class);
        if (rerankedResult == null || rerankedResult.getRankedDocuments() == null || rerankedResult.getRankedDocuments().isEmpty()) {
            log.warn("文档重排序节点：模型未返回有效重排序结果");
            return Map.of();
        }

        // 步骤5：使用Stream处理重排序结果，过滤低相关性文档，限制返回数量
        List<Document> finalDocuments = rerankedResult.getRankedDocuments().stream()
                .filter(rankedDoc -> rankedDoc.getRelevanceScore() >= RELEVANCE_THRESHOLD)  // 过滤低相关性文档
                .sorted(Comparator.comparingDouble(RankedDocument::getRelevanceScore).reversed())  // 按相关性降序排序
                .limit(FINAL_TOP_K)  // 限制返回数量
                .map(rankedDoc -> {
                    // 查找原始Document对象
                    return retrievedDocuments.get(rankedDoc.getDocId());
                })
                .filter(Objects::nonNull)  // 过滤掉未找到的文档
                .toList();

        log.debug("文档重排序节点：重排序后文档数量={}, 最终返回数量={}", 
                rerankedResult.getRankedDocuments().size(), finalDocuments.size());

        if (finalDocuments.isEmpty() || finalDocuments.size() < FINAL_TOP_K) {
            log.warn("文档重排序节点：重排序后无符合阈值的文档");
            Object o = config.context().get("rag_retry_count");
            if(o == null){
                config.context().put("rag_retry_count", 1);
                log.debug("文档重排序节点：开始重试");
                return Map.of();
            }
            int retryCount = (int) o;
            if(retryCount <= RETRY_COUNT){
                log.debug("文档重排序节点：重试次数加一，本轮重试次数={}", retryCount + 1);
                config.context().put("rag_retry_count", retryCount + 1);
                return Map.of();
            }
            log.debug("文档重排序节点：重试次数耗尽，结束RAG流程");
            config.context().put(GraphConstant.RAG_INTERRUPTED, true);
            return Map.of();
        }
        config.context().remove("rag_retry_count");

        // 将重排序后的文档存入state，供后续ConcatenationJoinNode使用
        return Map.of(NODE_NAME, finalDocuments);
    }

    /**
     * 重排序结果数据结构
     * <p>用于接收LLM模型输出的JSON格式重排序结果</p>
     */
    @Data
    public static class RerankedResult {
        /**
         * 重排序后的文档列表
         */
        private List<RankedDocument> rankedDocuments;
    }

    /**
     * 重排序文档数据结构
     * <p>包含文档ID和相关性评分</p>
     */
    @Data
    public static class RankedDocument {
        /**
         * 文档ID
         */
        private String docId;

        /**
         * 相关性评分（0-1之间）
         */
        private Double relevanceScore;
    }
}
