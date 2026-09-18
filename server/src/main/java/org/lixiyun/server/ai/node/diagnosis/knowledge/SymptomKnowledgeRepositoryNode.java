package org.lixiyun.server.ai.node.diagnosis.knowledge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeMatchRequest;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.lixiyun.server.ai.rag.milvus.MilvusUtil;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 症状知识库节点
 * @author lixiyun
 * @since 2026-08-12 13:04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SymptomKnowledgeRepositoryNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "symptomKnowledgeRepositoryNode";

    /** 每个查询返回的Top-K文档数量 */
    private static final int TOP_K = 5;
    /** 相似度阈值，低于此值的文档将被过滤 */
    private static final double SIMILARITY_THRESHOLD = 0.7;
    /** 重试次数 */
    private static final int RETRY_COUNT = 3;

    private static final String FILTER_EXPRESSION = "knowledge_type == 1 && deleted == 1";

    private final EmbeddingModel embeddingModel;
    private final MilvusUtil milvusUtil;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("知识侧-查询症状知识库-开始");

        Optional<KnowledgeMatchRequest> knowledgeMatchRequestOpt = state.value(KnowledgeMatchRequest.NAME);
        if (knowledgeMatchRequestOpt.isEmpty()) {
            log.error("知识侧-查询症状知识库-知识匹配请求为空");
            throw new BusinessException(ConversationExceptionEnum.KNOWLEDGE_MATCH_REQUEST_NOT_EXIST);
        }
        KnowledgeMatchRequest knowledgeMatchRequest = knowledgeMatchRequestOpt.get();

        if (!knowledgeMatchRequest.isSymptomNeedTransform() && knowledgeMatchRequest.getSymptomSliceIds() != null) {
            log.info("知识侧-查询症状知识库-跳过（无需重新查询，已有数据）");
            return Map.of();
        }

        String symptomPrompt = knowledgeMatchRequest.getSymptomPrompt();

        if (symptomPrompt == null || symptomPrompt.isBlank()) {
            log.warn("知识侧-查询症状知识库-症状Prompt为空，跳过检索");
            knowledgeMatchRequest.setSymptomSliceIds(Map.of());
            return Map.of();
        }

        // 1. 文本向量化
        float[] queryVector = embeddingModel.embed(symptomPrompt);
        // 2. 构建搜索参数（radius = 1 - similarityThreshold，适用于 COSINE 度量）
        //     radius 单独使用时，返回 distance <= radius 的结果（即相似度 >= threshold）
        Map<String, Object> searchParams = Map.of(
                "radius", 1.0 - SIMILARITY_THRESHOLD
        );
        // 3. 执行向量搜索
        List<MilvusUtil.SearchResult> results = milvusUtil.search(
                queryVector, FILTER_EXPRESSION, TOP_K, null, searchParams
        );

        Map<Long, Double> symptomSliceIds = results.stream()
                .collect(Collectors.toMap(
                        MilvusUtil.SearchResult::getId,
                        MilvusUtil.SearchResult::getScore
                ));

        knowledgeMatchRequest.setSymptomSliceIds(symptomSliceIds);

        log.info("知识侧-查询症状知识库-完成，检索到 {} 条记录", symptomSliceIds.size());
        return Map.of();
    }

    @Override
    public Object inputSummary(OverAllState state) {
        return state.value(KnowledgeMatchRequest.NAME).orElse(null);
    }

    @Override
    public Object outputSummary(OverAllState state) {
        Optional<KnowledgeMatchRequest> reqOpt = state.value(KnowledgeMatchRequest.NAME);
        return reqOpt.map(req -> Collections.singletonMap(
                "symptomSliceIds", (Object) req.getSymptomSliceIds()
        )).orElse(null);
    }
}