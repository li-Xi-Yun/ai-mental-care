package org.lixiyun.server.ai.node.diagnosis.graph;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.observation.GraphObservationLifecycleListener;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.common.redis.utils.RedisUtils;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisData;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.pojo.bo.conversation.state.GraphState;
import org.lixiyun.server.ai.node.diagnosis.*;
import org.lixiyun.server.ai.node.diagnosis.serializer.DiagnosisStateSerializer;
import org.lixiyun.server.ai.saver.CheckpointCleaner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 诊断主流程图构建器
 * <p>负责构建诊断主流程的工作流图，串接输入侧、知识侧、处理侧、持久化四个节点</p>
 * <p>流程：InputNode → KnowledgeNode → ProcessNode → DiagnosisPersistNode</p>
 *
 * @author lixiyun
 * @since 2026-08-14 12:30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisGraph {

    private final InputNode inputNode;
    private final IntentRecognitionNode intentRecognitionNode;
    private final KnowledgeNode knowledgeNode;
    private final ProcessNode processNode;
    private final DiagnosisPersistNode diagnosisPersistNode;
    private final ObservationRegistry observationRegistry;

    private final SaverConfig saverConfig;
    private final CheckpointCleaner checkpointCleaner;

    @Getter
    private static CompiledGraph diagnosisGraph;

    @PostConstruct
    public void init() {
        try {
            log.info("诊断主流程图构建器初始化");
            diagnosisGraph = buildGraph();
        } catch (Exception e) {
            log.error("诊断主流程图构建器初始化失败", e);
        }
    }

    /**
     * 执行诊断主流程图
     *
     * @param contextBO 会话处理上下文
     * @return 诊断数据结果
     */
    public DiagnosisData executeGraph(ConversationProcessContextBO contextBO) {
        log.debug("DiagnosisGraph-执行参数context:{}", contextBO);

        if (contextBO == null) {
            log.error("DiagnosisGraph-参数错误:会话上下文为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        ConversationMetadata metadata = ConversationMetadata.builder()
                .conversationId(contextBO.getConversation().getId())
                .userId(contextBO.getConversation().getUserId())
                .currentRound(contextBO.getConversation().getCurrentRound())
                .build();

        Map<String, Object> stateMap = Map.of(
                ConversationProcessContextBO.NAME, contextBO,
                ConversationMetadata.NAME, metadata,
                GraphState.NAME, new GraphState()
        );

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(contextBO.getConversation().getId() + "_diagnosis_" + System.currentTimeMillis())
                .addMetadata(ConversationMetadata.NAME, metadata)
                .build();

        Long conversationId = contextBO.getConversation().getId();
        String lockKey = "diagnosis:lock:" + conversationId;
        if (!RedisUtils.tryAcquireLock(lockKey, 0, 120)) {
            log.info("DiagnosisGraph-会话{}，轮次{}已有诊断进行中，跳过本次", conversationId, metadata.getCurrentRound());
            return null;
        }

        OverAllState result = null;
        try {
            result = diagnosisGraph.invoke(stateMap, runnableConfig).orElse(null);
            if (result == null) {
                log.error("DiagnosisGraph-执行失败，result为空");
                throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
            }
        } catch (BusinessException e) {
            throw new RuntimeException(e);
        } finally {
            RedisUtils.releaseLock(lockKey);
            try {
                checkpointCleaner.release(runnableConfig);
            } catch (Exception e) {
                log.error("DiagnosisGraph-Checkpoint删除失败", e);
            }
        }

        if (isDiagnosisInterrupted(result)) {
            log.info("DiagnosisGraph-诊断流程已中断，返回null");
            return null;
        }

        // result.value() 在通过 MysqlSaver 反序列化 checkpoint 时，
        // 由于 MysqlSaver 内部使用默认 Jackson 序列化器（未注册自定义类型），
        // 嵌套 POJO 可能被擦除为 LinkedHashMap，需要做兼容转换
        Object rawObj = result.value(DiagnosisData.NAME)
                .orElseThrow(() -> {
                    log.error("ProcessGraph-执行失败，diagnosisData为空");
                    return new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
                });

        DiagnosisData diagnosisData;
        if (rawObj instanceof DiagnosisData dd) {
            diagnosisData = dd;
        } else if (rawObj instanceof Map<?, ?> mapData) {
            log.warn("DiagnosisGraph-DiagnosisData类型被擦除为LinkedHashMap，执行JSON转换");
            diagnosisData = JsonUtils.convertMapToObj(mapData, DiagnosisData.class);
        } else {
            log.error("DiagnosisGraph-DiagnosisData类型不支持: {}", rawObj.getClass().getName());
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        log.info("DiagnosisGraph-执行成功");
        return diagnosisData;
    }

    /**
     * 构建诊断主流程工作流图
     * <p>按照以下流程构建诊断主流程图：</p>
     * <ul>
     *   <li>InputNode: 输入侧节点，调用输入侧图完成数据预清洗、结构化处理、情绪统计、归一化</li>
     *   <li>KnowledgeNode: 知识侧节点，调用知识侧图完成查询变换、知识库检索与重排</li>
     *   <li>ProcessNode: 处理侧节点，调用处理侧图完成情绪分析、病程归因、风险评估、干预建议、总结生成</li>
     *   <li>DiagnosisPersistNode: 持久化节点，将诊断数据写入诊断表</li>
     * </ul>
     *
     * @return {@link CompiledGraph} 编译后的诊断主流程图实例
     */
    private CompiledGraph buildGraph() {
        log.info("开始构建诊断主流程图");

        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(ConversationProcessContextBO.NAME, new ReplaceStrategy());
            keyStrategyMap.put(ConversationMetadata.NAME, new ReplaceStrategy());
            keyStrategyMap.put(InputResult.NAME, new ReplaceStrategy());
            keyStrategyMap.put(KnowledgeRetrieveResult.NAME, new ReplaceStrategy());
            keyStrategyMap.put(DiagnosisData.NAME, new ReplaceStrategy());
            keyStrategyMap.put(GraphState.NAME, new ReplaceStrategy());
            return keyStrategyMap;
        };

        CompiledGraph compiledGraph = null;

        try {
            StateGraph workflow = new StateGraph(keyStrategyFactory, (StateSerializer) new DiagnosisStateSerializer(OverAllState::new))
                    .addNode(IntentRecognitionNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(intentRecognitionNode))
                    .addNode(InputNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(inputNode))
                    .addNode(KnowledgeNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(knowledgeNode))
                    .addNode(ProcessNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(processNode))
                    .addNode(DiagnosisPersistNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(diagnosisPersistNode));

            workflow.addEdge(StateGraph.START, IntentRecognitionNode.NODE_NAME);
            workflow.addConditionalEdges(IntentRecognitionNode.NODE_NAME,
                    createInterruptConditionEdge(),
                    Map.of(GraphState.END, StateGraph.END,
                            GraphState.PROCESS, InputNode.NODE_NAME
                    )
            );
            workflow.addConditionalEdges(InputNode.NODE_NAME,
                    createInterruptConditionEdge(),
                    Map.of(GraphState.END, StateGraph.END,
                            GraphState.PROCESS, KnowledgeNode.NODE_NAME
                    )
            );
            workflow.addEdge(KnowledgeNode.NODE_NAME, ProcessNode.NODE_NAME);
            workflow.addEdge(ProcessNode.NODE_NAME, DiagnosisPersistNode.NODE_NAME);
            workflow.addEdge(DiagnosisPersistNode.NODE_NAME, StateGraph.END);

            compiledGraph = workflow.compile(
                    CompileConfig.builder()
                            .saverConfig(saverConfig)
                            .withLifecycleListener(new GraphObservationLifecycleListener(observationRegistry))
                            .build()
            );
            log.info("诊断主流程图构建完成");
        } catch (GraphStateException e) {
            log.error("诊断主流程图构建失败：{}", e.getMessage());
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        return compiledGraph;
    }

    /**
     * 创建诊断流程中断条件边
     * <p>从状态图中读取{@link GraphState}，检查任一阶段是否设置了中断标志，
     * 若中断则结束整个诊断流程，否则继续到下一节点</p>
     *
     * @return {@link AsyncEdgeActionWithConfig} 条件边动作
     */
    private AsyncEdgeActionWithConfig createInterruptConditionEdge() {
        return (state, config) -> {
            GraphState graphState = (GraphState) state.value(GraphState.NAME).orElse(null);
            if (graphState != null && isInterrupted(graphState)) {
                log.debug("条件跳转：诊断流程已中断，结束流程");
                return CompletableFuture.completedFuture(GraphState.END);
            }
            log.debug("条件跳转：诊断流程正常，继续到下一节点");
            return CompletableFuture.completedFuture(GraphState.PROCESS);
        };
    }

    /**
     * 判断GraphState中任一阶段是否处于中断状态
     *
     * @param graphState 图中断状态
     * @return true 表示有阶段中断
     */
    private boolean isInterrupted(GraphState graphState) {
        return (graphState.getInputGraphState() != null && graphState.getInputGraphState().isInterrupted())
                || (graphState.getKnowledgeGraphState() != null && graphState.getKnowledgeGraphState().isInterrupted())
                || (graphState.getProcessGraphState() != null && graphState.getProcessGraphState().isInterrupted());
    }

    /**
     * 判断诊断流程是否已被中断
     * <p>从{@link OverAllState}中读取{@link GraphState}，检查是否存在中断标识</p>
     *
     * @param result 图执行结果
     * @return true 表示诊断流程已中断，应提前返回
     */
    private boolean isDiagnosisInterrupted(OverAllState result) {
        if (result == null) {
            return false;
        }
        GraphState graphState = (GraphState) result.value(GraphState.NAME).orElse(null);
        return graphState != null && isInterrupted(graphState);
    }

}