package org.lixiyun.server.ai.node.diagnosis;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisData;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisDataRequest;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.server.ai.node.diagnosis.graph.ProcessGraph;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 诊断流程-处理侧节点
 * <p>从输入侧结果和知识侧结果构建处理侧请求，调用处理侧图完成情绪综合分析、病程归因、
 * 心理状态与症状评估、社会功能影响评估、保护性因素分析、风险评估、干预建议生成、总结生成</p>
 *
 * @author lixiyun
 * @since 2026-08-14 11:41
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "processNode";

    private final ProcessGraph processGraph;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断流程-处理侧节点-开始");

        Optional<InputResult> inputResultOpt = state.value(InputResult.NAME);
        if (inputResultOpt.isEmpty()) {
            log.error("诊断流程-处理侧节点-输入侧结果为空");
            throw new BusinessException(ConversationExceptionEnum.INPUT_RESULT_NOT_EXIST);
        }

        Optional<KnowledgeRetrieveResult> knowledgeRetrieveResultOpt = state.value(KnowledgeRetrieveResult.NAME);
        if (knowledgeRetrieveResultOpt.isEmpty()) {
            log.error("诊断流程-处理侧节点-知识侧结果为空");
            throw new BusinessException(ConversationExceptionEnum.KNOWLEDGE_RETRIEVE_RESULT_NOT_EXIST);
        }

        InputResult inputResult = inputResultOpt.get();
        KnowledgeRetrieveResult knowledgeRetrieveResult = knowledgeRetrieveResultOpt.get();

        DiagnosisDataRequest diagnosisDataRequest = DiagnosisDataRequest.builder()
                .inputResult(inputResult)
                .knowledgeRetrieveResult(knowledgeRetrieveResult)
                .build();

        DiagnosisData diagnosisData = processGraph.executeGraph(diagnosisDataRequest);

        log.info("诊断流程-处理侧节点-完成");
        return Map.of(DiagnosisData.NAME, diagnosisData);
    }
}