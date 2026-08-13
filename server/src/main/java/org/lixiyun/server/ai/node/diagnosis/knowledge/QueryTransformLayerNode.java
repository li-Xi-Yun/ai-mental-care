package org.lixiyun.server.ai.node.diagnosis.knowledge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeMatchRequest;
import org.lixiyun.server.ai.model.ChatModelFactory;
import org.lixiyun.server.ai.model.diagnosis.knowlegde.QueryTransformLayerModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识侧-查询变换层节点
 * @author lixiyun
 * @since 2026-08-12 13:04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTransformLayerNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "queryTransformLayerNode";

    private final QueryTransformLayerModel queryTransformLayerModel;
    private final ChatModelFactory chatModelFactory;
    private final ChatModel chatModel = chatModelFactory.getDeepSeekChatModel();

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("知识侧-查询变换层-开始");

        Optional<KnowledgeMatchRequest> knowledgeMatchRequestOpt = state.value(KnowledgeMatchRequest.NAME);
        if (knowledgeMatchRequestOpt.isEmpty()) {
            log.error("知识侧-查询变换层-知识匹配请求为空");
            throw new BusinessException(ConversationExceptionEnum.KNOWLEDGE_MATCH_REQUEST_NOT_EXIST);
        }
        KnowledgeMatchRequest knowledgeMatchRequest = knowledgeMatchRequestOpt.get();

        String userPrompt = buildUserPrompt(knowledgeMatchRequest);
        log.info("知识侧-查询变换层-构建用户提示词完成");

        QueryTransformLayerModel.QueryTransformLayerResult result = queryTransformLayerModel.callForResult(chatModel, userPrompt);

        if (result == null) {
            log.error("知识侧-查询变换层-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.KNOWLEDGE_MATCH_REQUEST_NOT_EXIST);
        }

        knowledgeMatchRequest.setSymptomPrompt(result.getSymptomPrompt());
        knowledgeMatchRequest.setDiagnosisPrompt(result.getDiagnosisPrompt());
        knowledgeMatchRequest.setInterventionPrompt(result.getInterventionPrompt());

        log.info("知识侧-查询变换层-完成，症状Query：{}，诊断Query：{}，干预Query：{}",
                result.getSymptomPrompt(), result.getDiagnosisPrompt(), result.getInterventionPrompt());
        return Map.of();
    }

    private String buildUserPrompt(KnowledgeMatchRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下结构化信息，生成面向三类知识库的检索Query：\n\n");

        List<String> standardSymptoms = request.getStandardSymptoms();
        if (standardSymptoms != null && !standardSymptoms.isEmpty()) {
            sb.append("## 标准症状列表\n");
            for (String symptom : standardSymptoms) {
                sb.append("- ").append(symptom).append("\n");
            }
            sb.append("\n");
        }

        String coreEmotion = request.getCoreEmotion();
        if (coreEmotion != null && !coreEmotion.isBlank()) {
            sb.append("## 主导情绪\n").append(coreEmotion).append("\n\n");
        }

        String coreAppeal = request.getCoreAppeal();
        if (coreAppeal != null && !coreAppeal.isBlank()) {
            sb.append("## 核心诉求\n").append(coreAppeal).append("\n\n");
        }

        sb.append("请分别生成：\n");
        sb.append("1. symptomPrompt：面向症状库的检索Query（基于标准症状列表）\n");
        sb.append("2. diagnosisPrompt：面向诊断标准库的检索Query（基于标准症状列表+主导情绪）\n");
        sb.append("3. interventionPrompt：面向干预方案库的检索Query（基于标准症状列表+核心诉求）\n");

        return sb.toString();
    }
}