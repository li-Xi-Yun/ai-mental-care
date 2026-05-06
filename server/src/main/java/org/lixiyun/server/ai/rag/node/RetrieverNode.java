package org.lixiyun.server.ai.rag.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.utils.SpringUtils;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 检索组件：实际用于检索向量数据库中数据的节点
 * <p>该节点接收多查询扩展后的查询列表，逐一进行向量检索，从向量数据库中获取相关文档，合并去重后返回</p>
 * @author lixiyun
 * @since 2026-04-27 08:58
 */
@Slf4j
@Builder
public class RetrieverNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "retrieverNode";

    private static final int TOP_K = 5;              // 每个查询返回的Top-K文档数量
    private static final double SIMILARITY_THRESHOLD = 0.7;  // 相似度阈值，低于此值的文档将被过滤
    private static final int RETRY_COUNT = 3;

    private final VectorStore vectorStore = SpringUtils.getBean(VectorStore.class);

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.debug("向量检索节点开始执行");

        // 获取扩展后的查询列表
        Optional<Object> expandedQueriesOpl = state.value(MultiQueryExpanderNode.NODE_NAME);
        List<String> queryList = expandedQueriesOpl
                .map(obj -> (List<String>) obj)
                .orElse(List.of());

        log.debug("向量检索节点：扩展查询数量={}", queryList.size());

        if (queryList.isEmpty()) {
            log.warn("向量检索节点：扩展查询列表为空，无法进行检索");
            config.context().put(GraphConstant.RAG_INTERRUPTED, true);
            return Map.of();
        }

        // 使用Stream并行处理多个查询的向量检索
        List<Document> allDocuments = queryList.parallelStream()
                .flatMap(query -> {
                    log.debug("向量检索节点：正在检索查询={}", query);
                    try {
                        // 构建检索请求
                        SearchRequest searchRequest = SearchRequest.builder()
                                .query(query)
                                .topK(TOP_K)
                                .similarityThreshold(SIMILARITY_THRESHOLD)
                                .build();

                        // 执行向量检索
                        List<Document> documents = vectorStore.similaritySearch(searchRequest);
                        log.debug("向量检索节点：查询=[{}] 检索到文档数量={}", query, documents.size());
                        return documents.stream();
                    } catch (Exception e) {
                        log.error("向量检索节点：查询=[{}] 检索失败", query, e);
                        return java.util.stream.Stream.empty();
                    }
                })
                .toList();

        log.info("向量检索节点：检索完成，总文档数量={}", allDocuments.size());

        if (allDocuments.isEmpty()) {
            log.warn("向量检索节点：未检索到任何相关文档");
            Object o = config.context().get("rag_retry_count");
            if(o == null){
                config.context().put("rag_retry_count", 1);
                log.debug("向量检索节点：开始重试");
                return Map.of();
            }
            int retryCount = (int) o;
            if(retryCount <= RETRY_COUNT){
                config.context().put("rag_retry_count", retryCount + 1);
                log.debug("向量检索节点：重试次数加一，本轮重试次数={}", retryCount + 1);
                return Map.of();
            }
            log.debug("向量检索节点：重试次数耗尽，结束RAG流程");
            config.context().put(GraphConstant.RAG_INTERRUPTED, true);
            return Map.of();
        }
        config.context().remove("rag_retry_count");

        // 使用Stream去重：基于文档ID去重
        Map<String, Document> documentMap = allDocuments.stream()
                .collect(Collectors.toMap(
                        Document::getId,              // 以ID作为key
                        doc -> doc,                   // 以Document本身作为value
                        (existing, replacement) -> existing  // 如果ID重复，保留第一个
                ));

        log.debug("向量检索节点：去重后文档数量={}", documentMap.size());

        // 将检索结果存入state，供后续ConcatenationJoinNode使用
        return Map.of(NODE_NAME, documentMap);
    }
}
