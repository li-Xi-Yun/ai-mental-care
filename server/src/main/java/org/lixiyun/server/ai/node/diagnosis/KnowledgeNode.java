package org.lixiyun.server.ai.node.diagnosis;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization.SymptomNormalizeResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.BaseInfo;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.EmotionStatisticsResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.CoreInfoExtractResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeMatchRequest;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.server.ai.node.diagnosis.graph.KnowledgeGraph;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 诊断流程-知识侧节点
 * <p>从输入侧结果中提取检索参数，调用知识侧图完成查询变换、知识库检索与重排</p>
 *
 * @author lixiyun
 * @since 2026-08-14 11:41
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "knowledgeNode";

    private final KnowledgeGraph knowledgeGraph;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断流程-知识侧节点-开始");

        Optional<InputResult> inputResultOpt = state.value(InputResult.NAME);
        if (inputResultOpt.isEmpty()) {
            log.error("诊断流程-知识侧节点-输入侧结果为空");
            throw new BusinessException(ConversationExceptionEnum.INPUT_RESULT_NOT_EXIST);
        }

        InputResult inputResult = inputResultOpt.get();
        KnowledgeMatchRequest knowledgeMatchRequest = buildKnowledgeMatchRequest(inputResult);

        KnowledgeRetrieveResult knowledgeRetrieveResult = knowledgeGraph.executeGraph(knowledgeMatchRequest);

        log.info("诊断流程-知识侧节点-完成");
        return Map.of(KnowledgeRetrieveResult.NAME, knowledgeRetrieveResult);
    }

    private KnowledgeMatchRequest buildKnowledgeMatchRequest(InputResult inputResult) {
        List<String> standardSymptoms = extractStandardSymptoms(inputResult.getSymptomNormalizeResult());
        String coreEmotion = extractCoreEmotion(inputResult.getEmotionStatisticsResult());
        String coreAppeal = extractCoreAppeal(inputResult.getCoreInfoExtractResult());

        return KnowledgeMatchRequest.builder()
                .standardSymptoms(standardSymptoms)
                .coreEmotion(coreEmotion)
                .coreAppeal(coreAppeal)
                .build();
    }

    private List<String> extractStandardSymptoms(SymptomNormalizeResult symptomNormalizeResult) {
        if (symptomNormalizeResult == null || symptomNormalizeResult.getTermList() == null) {
            return Collections.emptyList();
        }
        return symptomNormalizeResult.getTermList().stream()
                .filter(term -> term.getSymptomDict() != null && term.getSymptomDict().getSymptomTerm() != null)
                .map(term -> term.getSymptomDict().getSymptomTerm())
                .toList();
    }

    private String extractCoreEmotion(EmotionStatisticsResult emotionStatisticsResult) {
        if (emotionStatisticsResult == null || emotionStatisticsResult.getBaseInfo() == null) {
            return null;
        }
        BaseInfo baseInfo = emotionStatisticsResult.getBaseInfo();
        if (baseInfo.getDominantEmotion() != null) {
            return baseInfo.getDominantEmotion().getLabel();
        }
        return null;
    }

    private String extractCoreAppeal(CoreInfoExtractResult coreInfoExtractResult) {
        if (coreInfoExtractResult == null) {
            return null;
        }
        return coreInfoExtractResult.getCoreAppeal();
    }
}