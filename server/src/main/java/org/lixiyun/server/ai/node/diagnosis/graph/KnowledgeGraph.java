package org.lixiyun.server.ai.node.diagnosis.graph;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeMatchRequest;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.server.ai.node.diagnosis.knowledge.*;
import org.lixiyun.server.ai.node.diagnosis.serializer.KnowledgeStateSerializer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 知识侧图构建器
 * <p>负责构建知识侧的工作流图，包含查询变换层、症状知识库、诊断标准库、干预方案库、重排层等节点</p>
 * <p>流程：查询变换层 → 并行（症状知识库、诊断标准库、干预方案库）→ 重排层 → [条件边路由]</p>
 *
 * <h3>条件边重试机制</h3>
 * <ul>
 *   <li>重排层检测空结果后，通过条件边路由到查询变换层进行重试</li>
 *   <li>重排层设置needTransform标志，告知查询变换层哪些类型需要生成新Query</li>
 *   <li>知识查询节点根据needTransform标志决定是否执行查询，不需要的类型跳过</li>
 *   <li>超过最大重试次数 → 使用兜底内容返回</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-13 23:17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeGraph {

    private final QueryTransformLayerNode queryTransformLayerNode;
    private final DiagnosisStandardRepositoryNode diagnosisStandardRepositoryNode;
    private final InterventionPlanRepositoryNode interventionPlanRepositoryNode;
    private final SymptomKnowledgeRepositoryNode symptomKnowledgeRepositoryNode;
    private final RerankLayerNode rerankLayerNode;

    @Getter
    private static CompiledGraph knowledgeGraph;

    @PostConstruct
    public void init() {
        try {
            log.info("知识侧图构建器初始化");
            knowledgeGraph = buildGraph();
        } catch (Exception e) {
            log.error("知识侧图构建器初始化失败", e);
        }
    }

    /**
     * 执行知识侧图
     * <p>构建初始状态和配置，调用编译后的图执行，返回知识检索结果</p>
     *
     * @param knowledgeMatchRequest 知识匹配请求
     * @return 知识检索结果
     */
    public KnowledgeRetrieveResult executeGraph(KnowledgeMatchRequest knowledgeMatchRequest) {
        if (knowledgeMatchRequest == null) {
            log.error("KnowledgeGraph-参数错误:知识匹配请求为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor(SymptomKnowledgeRepositoryNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(DiagnosisStandardRepositoryNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(InterventionPlanRepositoryNode.NODE_NAME, ForkJoinPool.commonPool())
                .build();

        Map<String, Object> stateMap = Map.of(
                KnowledgeMatchRequest.NAME, knowledgeMatchRequest,
                KnowledgeRetrieveResult.NAME, new KnowledgeRetrieveResult()
        );

        OverAllState stateResult = knowledgeGraph.invoke(stateMap, runnableConfig).orElse(null);
        if (stateResult == null) {
            log.error("KnowledgeGraph-执行失败，result为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        Optional<KnowledgeRetrieveResult> resultOpt = stateResult.value(KnowledgeRetrieveResult.NAME);
        if (resultOpt.isEmpty()) {
            log.error("KnowledgeGraph-执行失败，knowledgeRetrieveResult为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        KnowledgeRetrieveResult result = resultOpt.get();
        log.info("KnowledgeGraph-执行完成，症状{}条，诊断{}条，干预{}条",
                result.getSymptomSliceList() != null ? result.getSymptomSliceList().size() : 0,
                result.getDiagnosisSliceList() != null ? result.getDiagnosisSliceList().size() : 0,
                result.getInterventionSliceList() != null ? result.getInterventionSliceList().size() : 0);

        return result;
    }

    /**
     * 构建知识侧工作流图
     * <p>按照以下流程构建知识侧图：</p>
     * <pre>
     * START → QueryTransformLayerNode ─┬→ SymptomKnowledgeRepositoryNode ──┐
     *                                   ├→ DiagnosisStandardRepositoryNode ─┼→ RerankLayerNode → [条件边]
     *                                   └→ InterventionPlanRepositoryNode ─┘       │
     *                                                                              ├→ "end" → END
     *                                                                              └→ "retry_all" → QueryTransformLayerNode
     * </pre>
     *
     * @return {@link CompiledGraph} 编译后的知识侧图实例
     */
    private CompiledGraph buildGraph() {
        log.info("开始构建知识侧图");

        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(KnowledgeMatchRequest.NAME, new ReplaceStrategy());
            keyStrategyMap.put(KnowledgeRetrieveResult.NAME, new ReplaceStrategy());
            keyStrategyMap.put(RerankLayerNode.ROUTING_DECISION_KEY, new ReplaceStrategy());
            return keyStrategyMap;
        };

        CompiledGraph compiledGraph = null;

        try {
            StateGraph workflow = new StateGraph(keyStrategyFactory, (StateSerializer) new KnowledgeStateSerializer(OverAllState::new))
                    .addNode(QueryTransformLayerNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(queryTransformLayerNode))
                    .addNode(SymptomKnowledgeRepositoryNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(symptomKnowledgeRepositoryNode))
                    .addNode(DiagnosisStandardRepositoryNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(diagnosisStandardRepositoryNode))
                    .addNode(InterventionPlanRepositoryNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(interventionPlanRepositoryNode))
                    .addNode(RerankLayerNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(rerankLayerNode));

            workflow.addEdge(StateGraph.START, QueryTransformLayerNode.NODE_NAME);
            workflow.addEdge(QueryTransformLayerNode.NODE_NAME, SymptomKnowledgeRepositoryNode.NODE_NAME);
            workflow.addEdge(QueryTransformLayerNode.NODE_NAME, DiagnosisStandardRepositoryNode.NODE_NAME);
            workflow.addEdge(QueryTransformLayerNode.NODE_NAME, InterventionPlanRepositoryNode.NODE_NAME);
            workflow.addEdge(SymptomKnowledgeRepositoryNode.NODE_NAME, RerankLayerNode.NODE_NAME);
            workflow.addEdge(DiagnosisStandardRepositoryNode.NODE_NAME, RerankLayerNode.NODE_NAME);
            workflow.addEdge(InterventionPlanRepositoryNode.NODE_NAME, RerankLayerNode.NODE_NAME);

            workflow.addConditionalEdges(
                    RerankLayerNode.NODE_NAME,
                    createRerankRoutingCondition(),
                    Map.of(
                            RerankLayerNode.ROUTING_END, StateGraph.END,
                            RerankLayerNode.ROUTING_RETRY_ALL, QueryTransformLayerNode.NODE_NAME
                    )
            );

            compiledGraph = workflow.compile();
            log.info("知识侧图构建完成");
        } catch (GraphStateException e) {
            log.error("知识侧图构建失败：{}", e.getMessage());
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        return compiledGraph;
    }

    /**
     * 创建重排层条件边路由函数
     * <p>从状态图中读取RerankLayerNode写入的路由决策，决定下一步跳转目标：</p>
     * <ul>
     *   <li>"end" → 结束流程</li>
     *   <li>"retry_all" → 回到查询变换层重试</li>
     * </ul>
     *
     * @return {@link AsyncEdgeActionWithConfig} 条件边动作
     */
    private AsyncEdgeActionWithConfig createRerankRoutingCondition() {
        return (state, config) -> {
            Optional<String> routingOpt = state.value(RerankLayerNode.ROUTING_DECISION_KEY);
            String routing = routingOpt.orElse(RerankLayerNode.ROUTING_END);
            log.debug("知识侧-条件边-路由决策：{}", routing);
            return CompletableFuture.completedFuture(routing);
        };
    }
}