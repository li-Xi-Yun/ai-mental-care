package org.lixiyun.server.ai.node.diagnosis.graph;

import com.alibaba.cloud.ai.graph.*;
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
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.server.ai.node.diagnosis.input.*;
import org.lixiyun.server.ai.node.diagnosis.serializer.InputStateSerializer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

/**
 * 输入侧图构建器
 * <p>负责构建输入侧的工作流图，包含数据预清洗、消息结构化处理、情绪数据处理、历史诊断摘要聚合、语义归一化处理等节点</p>
 * <p>流程：start → 数据预清洗 → 并行（消息结构化处理、情绪数据处理、历史诊断摘要聚合）→ 语义归一化处理 → end</p>
 *
 * @author lixiyun
 * @since 2026-08-13 22:53
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InputGraph {

    private final PreCleanNode preCleanNode;
    private final MessageStructuredProcessNode messageStructuredProcessNode;
    private final EmotionStatisticsNode emotionStatisticsNode;
    private final SymptomNormalizeNode symptomNormalizeNode;
    private final HistoryDiagnosisSummaryNode historyDiagnosisSummaryNode;

    @Getter
    private static CompiledGraph inputGraph;

    @PostConstruct
    public void init() {
        try {
            log.info("输入侧图构建器初始化");
            inputGraph = buildGraph();
        } catch (Exception e) {
            log.error("输入侧图构建器初始化失败", e);
        }
    }

    /**
     * 执行输入侧图
     *
     * @param contextBO 会话处理上下文
     * @param metadata  会话元数据
     * @return 输入侧流程聚合结果
     */
    public InputResult executeGraph(ConversationProcessContextBO contextBO, ConversationMetadata metadata) {
        if (contextBO == null) {
            log.error("InputGraph-参数错误:会话上下文为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor(MessageStructuredProcessNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(EmotionStatisticsNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(HistoryDiagnosisSummaryNode.NODE_NAME, ForkJoinPool.commonPool())
                .addMetadata(ConversationMetadata.NAME, metadata)
                .build();

        Map<String, Object> stateMap = Map.of(
                ConversationProcessContextBO.NAME, contextBO,
                ConversationMetadata.NAME, metadata,
                InputResult.NAME, new InputResult()
        );

        OverAllState result = inputGraph.invoke(stateMap, runnableConfig).orElse(null);
        if (result == null) {
            log.error("InputGraph-执行失败，result为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        // 替换原来直接强转代码
        Object rawObj = result.value(InputResult.NAME).orElseThrow(() -> new RuntimeException("子图未产出InputResult"));

        InputResult inputResult;
        if (rawObj instanceof InputResult) {
            inputResult = (InputResult) rawObj;
        } else if (rawObj instanceof Map<?, ?> mapData) {
            // map -> pojo，复用全局ObjectMapper
            inputResult = JsonUtils.convertMapToObj(mapData, InputResult.class);
        } else {
            throw new RuntimeException("InputResult类型不支持，类型：" + rawObj.getClass().getName());
        }

        log.debug("InputGraph-执行结果:{}", inputResult);

        return inputResult;
    }

    /**
     * 构建输入侧工作流图
     * <p>按照以下流程构建输入侧图：</p>
     * <ul>
     *   <li>PreCleanNode: 数据预清洗，合并排序消息并按轮次校验有效性</li>
     *   <li>并行执行：
     *     <ul>
     *       <li>MessageStructuredProcessNode: 消息结构化处理，提取核心信息</li>
     *       <li>EmotionStatisticsNode: 情绪数据处理，多维度情绪统计</li>
     *       <li>HistoryDiagnosisSummaryNode: 历史诊断摘要聚合，五维度分析</li>
     *     </ul>
     *   </li>
     *   <li>SymptomNormalizeNode: 语义归一化处理，症状标准化映射</li>
     * </ul>
     *
     * @return {@link CompiledGraph} 编译后的输入侧图实例
     */
    private CompiledGraph buildGraph() {
        log.info("开始构建输入侧图");

        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(ConversationProcessContextBO.NAME, new ReplaceStrategy());
            keyStrategyMap.put(ConversationMetadata.NAME, new ReplaceStrategy());
            keyStrategyMap.put(InputResult.NAME, new ReplaceStrategy());
            return keyStrategyMap;
        };

        CompiledGraph compiledGraph = null;

        try {
            StateGraph workflow = new StateGraph(keyStrategyFactory, (StateSerializer) new InputStateSerializer(OverAllState::new))
                    .addNode(PreCleanNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(preCleanNode))
                    .addNode(MessageStructuredProcessNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(messageStructuredProcessNode))
                    .addNode(EmotionStatisticsNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(emotionStatisticsNode))
                    .addNode(HistoryDiagnosisSummaryNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(historyDiagnosisSummaryNode))
                    .addNode(SymptomNormalizeNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(symptomNormalizeNode));

            workflow.addEdge(StateGraph.START, PreCleanNode.NODE_NAME);
            workflow.addEdge(PreCleanNode.NODE_NAME, MessageStructuredProcessNode.NODE_NAME);
            workflow.addEdge(PreCleanNode.NODE_NAME, EmotionStatisticsNode.NODE_NAME);
            workflow.addEdge(PreCleanNode.NODE_NAME, HistoryDiagnosisSummaryNode.NODE_NAME);
            workflow.addEdge(MessageStructuredProcessNode.NODE_NAME, SymptomNormalizeNode.NODE_NAME);
            workflow.addEdge(EmotionStatisticsNode.NODE_NAME, SymptomNormalizeNode.NODE_NAME);
            workflow.addEdge(HistoryDiagnosisSummaryNode.NODE_NAME, SymptomNormalizeNode.NODE_NAME);
            workflow.addEdge(SymptomNormalizeNode.NODE_NAME, StateGraph.END);

            compiledGraph = workflow.compile();
            log.info("输入侧图构建完成");
        } catch (GraphStateException e) {
            log.error("输入侧图构建失败：{}", e.getMessage());
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        return compiledGraph;
    }
}