package org.lixiyun.server.ai.node.diagnosis.knowledge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeMatchRequest;
import org.lixiyun.server.ai.model.diagnosis.knowlegde.QueryTransformLayerModel;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识侧-查询变换层节点
 *
 * <p>作为知识侧工作流图的入口节点，负责将上游传入的结构化信息（标准症状、主导情绪、核心诉求）
 * 转化为面向三类知识库的检索Query，供下游知识查询节点进行向量检索。</p>
 *
 * <h3>核心职责</h3>
 * <ol>
 *   <li>从状态图中读取{@link KnowledgeMatchRequest}，提取标准症状列表、主导情绪、核心诉求等结构化信息</li>
 *   <li>根据{@code needTransform}标志判断当前是首次执行还是重试执行，构建差异化的用户提示词</li>
 *   <li>调用{@link QueryTransformLayerModel}生成面向三类知识库的检索Query</li>
 *   <li>将生成的Query写回{@link KnowledgeMatchRequest}，供下游知识查询节点使用</li>
 * </ol>
 *
 * <h3>执行模式</h3>
 * <p>根据{@link KnowledgeMatchRequest}中的{@code needTransform}标志区分两种执行模式：</p>
 * <table>
 *   <tr><th>模式</th><th>触发条件</th><th>行为</th></tr>
 *   <tr>
 *     <td>首次执行</td>
 *     <td>三个{@code needTransform}标志均为{@code false}</td>
 *     <td>为三种知识库类型都生成检索Query</td>
 *   </tr>
 *   <tr>
 *     <td>重试执行</td>
 *     <td>至少一个{@code needTransform}标志为{@code true}</td>
 *     <td>仅为{@code needTransform=true}的类型生成新Query，其他类型保留原Query不变</td>
 *   </tr>
 * </table>
 *
 * <h3>Query降级策略</h3>
 * <p>重试执行时，每种类型的{@code queryLevel}独立递增，查询变换层根据级别生成不同复杂度的Query：</p>
 * <table>
 *   <tr><th>queryLevel</th><th>版本</th><th>生成规则</th></tr>
 *   <tr><td>0（默认）</td><td>精准版</td><td>症状+场景+限定词，追求精准匹配</td></tr>
 *   <tr><td>1</td><td>简化版</td><td>去掉场景限定词，保留核心症状+类型</td></tr>
 *   <tr><td>2</td><td>极简版</td><td>仅保留核心症状关键词，最大化召回</td></tr>
 * </table>
 *
 * <h3>与重排层的协作</h3>
 * <p>重排层（{@link RerankLayerNode}）在检测到空结果时，会设置{@code needTransform}标志并路由回本节点。
 * 本节点完成Query生成后，会将所有{@code needTransform}标志重置为{@code false}，
 * 下游知识查询节点通过{@code needTransform}和{@code sliceIds}的状态共同决定是否执行查询：</p>
 * <ul>
 *   <li>{@code needTransform=false} 且 {@code sliceIds!=null} → 跳过查询（已有数据）</li>
 *   <li>{@code needTransform=true} 或 {@code sliceIds=null} → 执行查询</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-12 13:04
 * @see KnowledgeMatchRequest
 * @see QueryTransformLayerModel
 * @see RerankLayerNode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTransformLayerNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "queryTransformLayerNode";

    private final QueryTransformLayerModel queryTransformLayerModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    /**
     * 节点执行入口
     * <p>从状态图中读取请求，根据{@code needTransform}标志判断执行模式，
     * 调用模型生成检索Query，写回请求对象并重置标志。</p>
     *
     * @param state  工作流全局状态，包含{@link KnowledgeMatchRequest}
     * @param config 运行时配置
     * @return 空Map（结果通过修改请求对象传递）
     * @throws BusinessException 当请求为空或模型输出解析失败时抛出
     */
    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("知识侧-查询变换层-开始");

        Optional<KnowledgeMatchRequest> knowledgeMatchRequestOpt = state.value(KnowledgeMatchRequest.NAME);
        if (knowledgeMatchRequestOpt.isEmpty()) {
            log.error("知识侧-查询变换层-知识匹配请求为空");
            throw new BusinessException(ConversationExceptionEnum.KNOWLEDGE_MATCH_REQUEST_NOT_EXIST);
        }
        KnowledgeMatchRequest knowledgeMatchRequest = knowledgeMatchRequestOpt.get();

        boolean symptomNeed = knowledgeMatchRequest.isSymptomNeedTransform();
        boolean diagnosisNeed = knowledgeMatchRequest.isDiagnosisNeedTransform();
        boolean interventionNeed = knowledgeMatchRequest.isInterventionNeedTransform();

        boolean isFirstRun = !symptomNeed && !diagnosisNeed && !interventionNeed;

        if (isFirstRun) {
            log.info("知识侧-查询变换层-首次执行，为三种类型生成Query");
        } else {
            log.info("知识侧-查询变换层-重试执行，需要生成Query的类型[症状={},诊断={},干预={}]",
                    symptomNeed, diagnosisNeed, interventionNeed);
        }

        String userPrompt = buildUserPrompt(knowledgeMatchRequest, isFirstRun, symptomNeed, diagnosisNeed, interventionNeed);
        log.debug("知识侧-查询变换层-构建用户提示词完成，提示词：{}", userPrompt);

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfigWithLoad(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
        QueryTransformLayerModel.QueryTransformLayerResult result = queryTransformLayerModel.callForResult(chatModel, userPrompt, aiNodeConfig, config);
        log.debug("知识侧-查询变换层-模型返回结果：{}", result);

        if (result == null) {
            log.error("知识侧-查询变换层-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.KNOWLEDGE_MATCH_REQUEST_NOT_EXIST);
        }

        if (isFirstRun || symptomNeed) {
            knowledgeMatchRequest.setSymptomPrompt(result.getSymptomPrompt());
        }
        if (isFirstRun || diagnosisNeed) {
            knowledgeMatchRequest.setDiagnosisPrompt(result.getDiagnosisPrompt());
        }
        if (isFirstRun || interventionNeed) {
            knowledgeMatchRequest.setInterventionPrompt(result.getInterventionPrompt());
        }

        log.debug("知识侧-查询变换层-写入结果完成，症状Query：{}，诊断Query：{}，干预Query：{}",
                result.getSymptomPrompt(), result.getDiagnosisPrompt(), result.getInterventionPrompt());

        log.info("知识侧-查询变换层-完成，症状Query：{}，诊断Query：{}，干预Query：{}",
                knowledgeMatchRequest.getSymptomPrompt(),
                knowledgeMatchRequest.getDiagnosisPrompt(),
                knowledgeMatchRequest.getInterventionPrompt());
        return Map.of();
    }

    /**
     * 构建面向模型的用户提示词
     * <p>根据执行模式构建差异化提示词：</p>
     * <ul>
     *   <li>首次执行：要求模型为三类知识库都生成检索Query</li>
     *   <li>重试执行：明确告知模型哪些类型需要生成、哪些不需要，并附带各类型的queryLevel降级级别</li>
     * </ul>
     *
     * @param request          知识匹配请求，包含标准症状、主导情绪、核心诉求等结构化信息
     * @param isFirstRun       是否首次执行
     * @param symptomNeed      症状库是否需要生成新Query
     * @param diagnosisNeed    诊断标准库是否需要生成新Query
     * @param interventionNeed 干预方案库是否需要生成新Query
     * @return 构建完成的用户提示词
     */
    private String buildUserPrompt(KnowledgeMatchRequest request,
                                   boolean isFirstRun,
                                   boolean symptomNeed,
                                   boolean diagnosisNeed,
                                   boolean interventionNeed) {
        StringBuilder sb = new StringBuilder();

        if (isFirstRun) {
            sb.append("请根据以下结构化信息，生成面向三类知识库的检索Query：\n\n");
        } else {
            sb.append("请根据以下结构化信息，仅为指定的知识库生成检索Query（无需生成的类型可以输出空字符串）：\n\n");
            sb.append("## 需要生成Query的知识库\n");
            if (symptomNeed) {
                sb.append("- 症状库：需要（queryLevel=").append(request.getSymptomQueryLevel())
                        .append("，").append(describeQueryLevel(request.getSymptomQueryLevel())).append("）\n");
            } else {
                sb.append("- 症状库：不需要（保留原Query）\n");
            }
            if (diagnosisNeed) {
                sb.append("- 诊断标准库：需要（queryLevel=").append(request.getDiagnosisQueryLevel())
                        .append("，").append(describeQueryLevel(request.getDiagnosisQueryLevel())).append("）\n");
            } else {
                sb.append("- 诊断标准库：不需要（保留原Query）\n");
            }
            if (interventionNeed) {
                sb.append("- 干预方案库：需要（queryLevel=").append(request.getInterventionQueryLevel())
                        .append("，").append(describeQueryLevel(request.getInterventionQueryLevel())).append("）\n");
            } else {
                sb.append("- 干预方案库：不需要（保留原Query）\n");
            }
            sb.append("\n");
        }

        boolean hasLevelInfo = request.getSymptomQueryLevel() > 0
                || request.getDiagnosisQueryLevel() > 0
                || request.getInterventionQueryLevel() > 0;
        if (hasLevelInfo && isFirstRun) {
            sb.append("## 重要：当前Query降级级别\n");
            sb.append("各知识库的Query降级级别可能不同，请严格按照系统提示中的Query降级策略生成对应复杂度的Query：\n");
            sb.append("- 症状库 queryLevel=").append(request.getSymptomQueryLevel())
                    .append("（").append(describeQueryLevel(request.getSymptomQueryLevel())).append("）\n");
            sb.append("- 诊断标准库 queryLevel=").append(request.getDiagnosisQueryLevel())
                    .append("（").append(describeQueryLevel(request.getDiagnosisQueryLevel())).append("）\n");
            sb.append("- 干预方案库 queryLevel=").append(request.getInterventionQueryLevel())
                    .append("（").append(describeQueryLevel(request.getInterventionQueryLevel())).append("）\n\n");
        }

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

    /**
     * 描述Query降级级别的中文含义
     *
     * @param level 降级级别（0=精准版，1=简化版，2=极简版）
     * @return 级别对应的中文描述
     */
    private String describeQueryLevel(int level) {
        return switch (level) {
            case 1 -> "简化版（去掉场景限定词，保留核心症状+类型）";
            case 2 -> "极简版（仅保留核心症状关键词，最大化召回）";
            default -> "精准版";
        };
    }

    @Override
    public Object inputSummary(OverAllState state) {
        return state.value(KnowledgeMatchRequest.NAME).orElse(null);
    }

    @Override
    public Object outputSummary(OverAllState state) {
        Optional<KnowledgeMatchRequest> reqOpt = state.value(KnowledgeMatchRequest.NAME);
        return reqOpt.map(req -> Map.ofEntries(
                Map.entry("symptomPrompt", req.getSymptomPrompt()),
                Map.entry("diagnosisPrompt", req.getDiagnosisPrompt()),
                Map.entry("interventionPrompt", req.getInterventionPrompt())
        )).orElse(null);
    }
}