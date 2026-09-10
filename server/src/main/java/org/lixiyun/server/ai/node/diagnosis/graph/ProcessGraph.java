package org.lixiyun.server.ai.node.diagnosis.graph;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisData;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisDataRequest;
import org.lixiyun.server.ai.node.diagnosis.process.*;
import org.lixiyun.server.ai.node.diagnosis.serializer.ProcessStateSerializer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

/**
 * 处理侧图构建器
 * <p>负责构建处理侧的工作流图，包含情绪综合分析、病程归因、心理状态与症状评估、
 * 社会功能影响评估、保护性因素分析、风险评估、干预建议生成、总结生成等节点</p>
 * <p>流程：并行（情绪综合分析组、病程归因组、心理状态与症状评估、社会功能影响评估、保护性因素分析、风险评估）→ 干预建议生成 → 总结生成</p>
 *
 * @author lixiyun
 * @since 2026-08-13 23:28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessGraph {

    private final ComprehensiveDiagnosisNode comprehensiveDiagnosisNode;
    private final DiseaseCourseAttributionNode diseaseCourseAttributionNode;
    private final PsychologicalStateNode psychologicalStateNode;
    private final SocialFunctionImpactNode socialFunctionImpactNode;
    private final ProtectiveFactorNode protectiveFactorNode;
    private final RiskAssessmentNode riskAssessmentNode;
    private final InterventionSuggestionNode interventionSuggestionNode;
    private final DiagnosisSummaryNode diagnosisSummaryNode;

    @Getter
    private static CompiledGraph processGraph;

    @PostConstruct
    public void init() {
        try {
            log.info("处理侧图构建器初始化");
            processGraph = buildGraph();
        } catch (Exception e) {
            log.error("处理侧图构建器初始化失败", e);
        }
    }

    /**
     * 执行处理侧图
     *
     * @param diagnosisDataRequest 处理侧提示词数据
     * @return 诊断数据结果
     */
    public DiagnosisData executeGraph(DiagnosisDataRequest diagnosisDataRequest) {
        if (diagnosisDataRequest == null) {
            log.error("ProcessGraph-参数错误:诊断数据请求为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor(ComprehensiveDiagnosisNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(DiseaseCourseAttributionNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(PsychologicalStateNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(SocialFunctionImpactNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(ProtectiveFactorNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(RiskAssessmentNode.NODE_NAME, ForkJoinPool.commonPool())
                .build();

        Map<String, Object> stateMap = Map.of(
                DiagnosisDataRequest.NAME, diagnosisDataRequest,
                DiagnosisData.NAME, new DiagnosisData()
        );

        OverAllState result = processGraph.invoke(stateMap, runnableConfig).orElse(null);
        if (result == null) {
            log.error("ProcessGraph-执行失败，result为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        Optional<DiagnosisData> diagnosisDataOpt = result.value(DiagnosisData.NAME);
        if (diagnosisDataOpt.isEmpty()) {
            log.error("ProcessGraph-执行失败，diagnosisData为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        log.info("ProcessGraph-执行成功");
        return diagnosisDataOpt.get();
    }

    /**
     * 构建处理侧工作流图
     * <p>按照以下流程构建处理侧图：</p>
     * <ul>
     *   <li>并行执行：
     *     <ul>
     *       <li>ComprehensiveDiagnosisNode: 情绪综合分析组，综合情绪数据生成情绪诊断结论</li>
     *       <li>DiseaseCourseAttributionNode: 病程归因组，分析症状持续时间与触发因素</li>
     *       <li>PsychologicalStateNode: 心理状态与症状评估，评估心理状态与核心症状</li>
     *       <li>SocialFunctionImpactNode: 社会功能影响评估，评估社会功能受损程度</li>
     *       <li>ProtectiveFactorNode: 保护性因素分析，分析社会支持与应对方式</li>
     *       <li>RiskAssessmentNode: 风险评估，评估情绪风险、自伤风险与自杀风险</li>
     *     </ul>
     *   </li>
     *   <li>InterventionSuggestionNode: 干预建议生成，基于评估结果生成分级干预建议</li>
     *   <li>DiagnosisSummaryNode: 总结生成，生成诊断书核心内容</li>
     * </ul>
     *
     * @return {@link CompiledGraph} 编译后的处理侧图实例
     */
    private CompiledGraph buildGraph() {
        log.info("开始构建处理侧图");

        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(DiagnosisDataRequest.NAME, new ReplaceStrategy());
            keyStrategyMap.put(DiagnosisData.NAME, new ReplaceStrategy());
            return keyStrategyMap;
        };

        CompiledGraph compiledGraph = null;

        try {
            StateGraph workflow = new StateGraph(keyStrategyFactory, (StateSerializer) new ProcessStateSerializer(OverAllState::new))
                    .addNode(ComprehensiveDiagnosisNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(comprehensiveDiagnosisNode))
                    .addNode(DiseaseCourseAttributionNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(diseaseCourseAttributionNode))
                    .addNode(PsychologicalStateNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(psychologicalStateNode))
                    .addNode(SocialFunctionImpactNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(socialFunctionImpactNode))
                    .addNode(ProtectiveFactorNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(protectiveFactorNode))
                    .addNode(RiskAssessmentNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(riskAssessmentNode))
                    .addNode(InterventionSuggestionNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(interventionSuggestionNode))
                    .addNode(DiagnosisSummaryNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(diagnosisSummaryNode));

            workflow.addEdge(StateGraph.START, ComprehensiveDiagnosisNode.NODE_NAME);
            workflow.addEdge(StateGraph.START, DiseaseCourseAttributionNode.NODE_NAME);
            workflow.addEdge(StateGraph.START, PsychologicalStateNode.NODE_NAME);
            workflow.addEdge(StateGraph.START, SocialFunctionImpactNode.NODE_NAME);
            workflow.addEdge(StateGraph.START, ProtectiveFactorNode.NODE_NAME);
            workflow.addEdge(StateGraph.START, RiskAssessmentNode.NODE_NAME);
            workflow.addEdge(ComprehensiveDiagnosisNode.NODE_NAME, InterventionSuggestionNode.NODE_NAME);
            workflow.addEdge(DiseaseCourseAttributionNode.NODE_NAME, InterventionSuggestionNode.NODE_NAME);
            workflow.addEdge(PsychologicalStateNode.NODE_NAME, InterventionSuggestionNode.NODE_NAME);
            workflow.addEdge(SocialFunctionImpactNode.NODE_NAME, InterventionSuggestionNode.NODE_NAME);
            workflow.addEdge(ProtectiveFactorNode.NODE_NAME, InterventionSuggestionNode.NODE_NAME);
            workflow.addEdge(RiskAssessmentNode.NODE_NAME, InterventionSuggestionNode.NODE_NAME);
            workflow.addEdge(InterventionSuggestionNode.NODE_NAME, DiagnosisSummaryNode.NODE_NAME);
            workflow.addEdge(DiagnosisSummaryNode.NODE_NAME, StateGraph.END);

            compiledGraph = workflow.compile();
            log.info("处理侧图构建完成");
        } catch (GraphStateException e) {
            log.error("处理侧图构建失败：{}", e.getMessage());
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        return compiledGraph;
    }
}