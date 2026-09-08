package org.lixiyun.server.ai.node.diagnosis.knowledge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeMatchRequest;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeSliceItem;
import org.lixiyun.pojo.entity.conversation.KnowledgeDocument;
import org.lixiyun.pojo.entity.file.InfraFile;
import org.lixiyun.server.ai.model.diagnosis.knowlegde.RerankLayerModel;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.server.mapper.InfraFileMapper;
import org.lixiyun.server.mapper.KnowledgeDocumentMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 重排层节点
 *
 * <p>作为知识侧工作流图的核心决策节点，承担两大职责：</p>
 * <ol>
 *   <li><b>重排</b>：对向量召回的粗结果做二次语义校验，基于模型相关性分与向量相似度分加权打分，
 *       过滤低质内容、去重截断，最终输出高相关的核心素材</li>
 *   <li><b>重试决策</b>：通过前置检查和后置检查两个阶段，检测空结果并决定是路由回查询变换层重试，
 *       还是使用兜底内容结束流程</li>
 * </ol>
 *
 * <h3>核心流程（三阶段）</h3>
 * <pre>
 * 阶段1-前置检查：检查上游数据是否为空
 *   ├─ 有空数据 + 对应类型有重试次数 → 设置needTransform标志，路由到查询变换层
 *   ├─ 有空数据 + 对应类型无重试次数 → 空库标记兜底，有数据的库继续重排
 *   └─ 全部有数据 → 继续重排
 *
 * 阶段2-重排：仅对有数据的库执行重排逻辑
 *   向量召回粗结果 → 构建提示词 → 模型打分 → 加权综合分 → 阈值过滤 → TopN截断
 *
 * 阶段3-后置检查：检查重排后结果是否为空
 *   ├─ 重排后为空 + 对应类型有重试次数 → 设置needTransform标志，路由到查询变换层
 *   └─ 重排后为空 + 对应类型无重试次数 → 兜底内容
 * </pre>
 *
 * <h3>重试计数规则</h3>
 * <ul>
 *   <li>三种类型（症状、诊断、干预）各自独立计数，每种最多重试{@value #MAX_RETRY_COUNT}次</li>
 *   <li>任一类型的重试次数耗尽，该类型即使用兜底内容，不影响其他类型的正常流程</li>
 *   <li>所有重试统一路由到查询变换层，由查询变换层根据{@code needTransform}标志决定哪些类型需要生成新Query</li>
 *   <li>每次重试时，对应类型的{@code retryCount}递增1，{@code queryLevel}递增1（最大为2）</li>
 * </ul>
 *
 * <h3>needTransform标志机制</h3>
 * <p>本节点在每次执行开始时，将三个{@code needTransform}标志重置为{@code false}。
 * 在前置检查或后置检查中，若发现某类型为空且仍有重试次数，则将对应标志置为{@code true}。
 * 路由到查询变换层后：</p>
 * <ul>
 *   <li>查询变换层仅为{@code needTransform=true}的类型生成新Query</li>
 *   <li>知识查询节点通过{@code needTransform}和{@code sliceIds}的状态共同决定是否执行查询</li>
 *   <li>查询变换层完成生成后，将所有{@code needTransform}标志重置为{@code false}</li>
 * </ul>
 *
 * <h3>兜底内容</h3>
 * <p>当某类型重试次数耗尽仍无有效结果时，使用预定义的通用心理健康科普内容作为兜底。
 * 兜底内容仅提供通用、无风险的参考信息，不包含具体诊断或针对性干预方案，
 * 并明确标注为通用参考以避免误导。</p>
 *
 * <h3>综合分公式</h3>
 * <pre>最终综合分 = 模型相关性分 × {@value #modelScoreWeight} + 向量相似度分 × {@value #vectorScoreWeight}</pre>
 *
 * <h3>TopN截断规则</h3>
 * <ul>
 *   <li>症状库：最多保留{@value #maxSymptomCount}条</li>
 *   <li>诊断标准库：最多保留{@value #maxDiagnosisCount}条</li>
 *   <li>干预方案库：最多保留{@value #maxInterventionCount}条</li>
 * </ul>
 *
 * <h3>条件边路由</h3>
 * <p>本节点通过{@value #ROUTING_DECISION_KEY}向状态图写入路由决策，
 * 由{@link org.lixiyun.server.ai.node.diagnosis.graph.KnowledgeGraph}中的条件边读取并路由：</p>
 * <ul>
 *   <li>{@value #ROUTING_END} → 结束流程，进入{@link StateGraph#END}</li>
 *   <li>{@value #ROUTING_RETRY_ALL} → 回到查询变换层重试</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-12 13:05
 * @see KnowledgeMatchRequest
 * @see KnowledgeRetrieveResult
 * @see org.lixiyun.server.ai.node.diagnosis.graph.KnowledgeGraph
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankLayerNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "rerankLayerNode";

    /** 状态图中路由决策的Key */
    public static final String ROUTING_DECISION_KEY = "rerankRoutingDecision";

    /** 路由决策：结束流程 */
    public static final String ROUTING_END = "end";
    /** 路由决策：回到查询变换层重试 */
    public static final String ROUTING_RETRY_ALL = "retry_all";

    /** 模型分值权重 */
    private static final double modelScoreWeight = 0.7;
    /** 向量分值权重 */
    private static final double vectorScoreWeight = 0.3;
    /** 最小综合分阈值 */
    private static final double minScoreThreshold = 0.5;
    /** 症状库最大保留条数 */
    private static final int maxSymptomCount = 3;
    /** 诊断标准库最大保留条数 */
    private static final int maxDiagnosisCount = 2;
    /** 干预方案库最大保留条数 */
    private static final int maxInterventionCount = 4;
    /** 每种类型的最大重试次数 */
    private static final int MAX_RETRY_COUNT = 3;

    /** 症状库兜底参考提示词 */
    private static final String FALLBACK_SYMPTOM_PROMPT = """
            【症状参考（兜底）】
            以下为通用心理健康症状科普内容，仅供参考，不构成具体诊断：
            
            1. 常见情绪症状：持续的情绪低落、焦虑不安、易怒烦躁、情绪波动大等，当上述症状持续超过两周且影响日常生活时，建议关注心理健康状态。
            2. 常见躯体症状：入睡困难或早醒、食欲明显变化、疲劳乏力、注意力难以集中等，这些躯体症状常与心理状态密切相关。
            3. 常见行为症状：社交退缩、兴趣丧失、回避行为、反复确认行为等，行为模式的显著改变可能是心理问题的信号。
            
            ⚠️ 以上内容为通用症状参考信息，不构成任何诊断结论。如有需要，请务必咨询专业心理健康服务提供者。
            """;

    /** 诊断标准库兜底参考提示词 */
    private static final String FALLBACK_DIAGNOSIS_PROMPT = """
            【诊断标准参考（兜底）】
            以下为通用心理健康诊断科普内容，仅供参考，不构成具体诊断：
            
            1. 心理健康评估的一般原则：心理状态的评估需综合考虑症状持续时间、严重程度、社会功能损害程度等多维度因素，单一症状不足以构成诊断。
            2. 常见心理状态分类：焦虑状态、抑郁状态、应激反应、适应障碍等，这些状态在人群中较为常见，多数经过适当干预可得到缓解。
            3. 诊断注意事项：心理状态的判断需要由专业人员进行，自我评估仅供参考，不可替代专业诊断。
            
            ⚠️ 以上内容为通用诊断标准参考信息，不构成任何诊断结论。如有需要，请务必咨询专业心理健康服务提供者。
            """;

    /** 干预方案库兜底参考提示词 */
    private static final String FALLBACK_INTERVENTION_PROMPT = """
            【干预方案参考（兜底）】
            以下为通用心理健康干预科普内容，仅供参考，不构成针对性干预建议：
            
            1. 情绪调节的通用原则：当感到情绪低落或焦虑时，可以尝试深呼吸、正念冥想、适度运动等方式进行自我调节，保持规律的作息和健康的饮食习惯有助于情绪稳定。
            2. 压力管理建议：面对生活或工作压力时，建议合理规划时间、分解任务、设定优先级，必要时学会说"不"，避免过度承担。
            3. 社交支持的重要性：与信任的家人、朋友保持沟通，分享内心感受，不要独自承受压力，社交支持是心理健康的重要保护因素。
            4. 何时寻求专业帮助：如果情绪困扰持续超过两周、严重影响日常生活和工作、出现自伤或伤人念头，建议及时寻求专业心理咨询或精神科医生的帮助。
            5. 危机干预热线：如遇紧急心理危机，可拨打全国心理援助热线：400-161-9995，或当地精神卫生中心热线。
            
            ⚠️ 以上内容为通用参考信息，不构成任何针对性干预方案。如有需要，请务必咨询专业心理健康服务提供者。
            """;

    private final ChatModelFactory chatModelFactory;
    private final RerankLayerModel rerankLayerModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final InfraFileMapper infraFileMapper;

    /**
     * 节点执行入口
     * <p>按三阶段顺序执行：前置检查 → 重排 → 后置检查，通过{@value #ROUTING_DECISION_KEY}输出路由决策。</p>
     * <p>每次执行开始时，先将三个{@code needTransform}标志重置为{@code false}，
     * 避免上一轮的标志影响本轮判断。</p>
     *
     * @param state  工作流全局状态，包含{@link KnowledgeMatchRequest}和{@link KnowledgeRetrieveResult}
     * @param config 运行时配置
     * @return 包含路由决策的Map，key为{@value #ROUTING_DECISION_KEY}
     * @throws BusinessException 当请求或结果为空、模型输出解析失败时抛出
     */
    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("知识侧-重排层节点-开始");

        Optional<KnowledgeMatchRequest> requestOpt = state.value(KnowledgeMatchRequest.NAME);
        if (requestOpt.isEmpty()) {
            log.error("知识侧-重排层节点-知识匹配请求为空");
            throw new BusinessException(ConversationExceptionEnum.KNOWLEDGE_MATCH_REQUEST_NOT_EXIST);
        }
        KnowledgeMatchRequest request = requestOpt.get();

        Optional<KnowledgeRetrieveResult> resultOpt = state.value(KnowledgeRetrieveResult.NAME);
        if (resultOpt.isEmpty()) {
            log.error("知识侧-重排层节点-知识检索结果为空");
            throw new BusinessException(ConversationExceptionEnum.KNOWLEDGE_MATCH_REQUEST_NOT_EXIST);
        }
        KnowledgeRetrieveResult result = resultOpt.get();

        request.setSymptomNeedTransform(false);
        request.setDiagnosisNeedTransform(false);
        request.setInterventionNeedTransform(false);

        // ==================== 阶段1：前置检查 ====================
        String preRouting = preProcessCheck(request, result);
        if (preRouting != null) {
            log.info("知识侧-重排层节点-前置检查路由决策：{}，重试次数[症状={},诊断={},干预={}]",
                    preRouting, request.getSymptomRetryCount(), request.getDiagnosisRetryCount(),
                    request.getInterventionRetryCount());
            return Map.of(ROUTING_DECISION_KEY, preRouting);
        }

        // ==================== 阶段2：重排 ====================
        RerankOutput rerankOutput = executeRerank(request);

        // ==================== 阶段3：后置检查 ====================
        String postRouting = postRerankCheck(request, result, rerankOutput);
        log.info("知识侧-重排层节点-后置检查路由决策：{}，重试次数[症状={},诊断={},干预={}]",
                postRouting, request.getSymptomRetryCount(), request.getDiagnosisRetryCount(),
                request.getInterventionRetryCount());

        return Map.of(ROUTING_DECISION_KEY, postRouting);
    }

    // ==================== 阶段1：前置检查 ====================

    /**
     * 前置检查：判断上游数据是否为空，决定路由或标记兜底
     * <ul>
     *   <li>全部有数据 → 返回null，继续重排</li>
     *   <li>有空数据 + 对应类型有重试次数 → 设置needTransform=true，路由到查询变换层</li>
     *   <li>有空数据 + 对应类型无重试次数 → 空库标记兜底，有数据的库继续重排</li>
     * </ul>
     *
     * @return 路由决策字符串，null表示继续重排
     */
    private String preProcessCheck(KnowledgeMatchRequest request, KnowledgeRetrieveResult result) {
        boolean symptomEmpty = isSliceIdsEmpty(request.getSymptomSliceIds());
        boolean diagnosisEmpty = isSliceIdsEmpty(request.getDiagnosisSliceIds());
        boolean interventionEmpty = isSliceIdsEmpty(request.getInterventionSliceIds());
        int preEmptyCount = (symptomEmpty ? 1 : 0) + (diagnosisEmpty ? 1 : 0) + (interventionEmpty ? 1 : 0);

        if (preEmptyCount == 0) {
            log.info("知识侧-重排层节点-前置检查：三库均有上游数据，进入重排阶段");
            return null;
        }

        log.info("知识侧-重排层节点-前置检查：存在空数据，空库数={}，重试次数[症状={},诊断={},干预={}]",
                preEmptyCount, request.getSymptomRetryCount(), request.getDiagnosisRetryCount(),
                request.getInterventionRetryCount());

        boolean anyNeedTransform = false;

        if (symptomEmpty) {
            if (request.getSymptomRetryCount() >= MAX_RETRY_COUNT) {
                log.info("知识侧-重排层节点-前置检查：症状库重试次数耗尽，使用兜底内容");
                applyFallbackContent(result, true, false, false);
            } else {
                request.setSymptomNeedTransform(true);
                request.setSymptomRetryCount(request.getSymptomRetryCount() + 1);
                request.setSymptomQueryLevel(Math.min(request.getSymptomQueryLevel() + 1, 2));
                anyNeedTransform = true;
            }
        }

        if (diagnosisEmpty) {
            if (request.getDiagnosisRetryCount() >= MAX_RETRY_COUNT) {
                log.info("知识侧-重排层节点-前置检查：诊断标准库重试次数耗尽，使用兜底内容");
                applyFallbackContent(result, false, true, false);
            } else {
                request.setDiagnosisNeedTransform(true);
                request.setDiagnosisRetryCount(request.getDiagnosisRetryCount() + 1);
                request.setDiagnosisQueryLevel(Math.min(request.getDiagnosisQueryLevel() + 1, 2));
                anyNeedTransform = true;
            }
        }

        if (interventionEmpty) {
            if (request.getInterventionRetryCount() >= MAX_RETRY_COUNT) {
                log.info("知识侧-重排层节点-前置检查：干预方案库重试次数耗尽，使用兜底内容");
                applyFallbackContent(result, false, false, true);
            } else {
                request.setInterventionNeedTransform(true);
                request.setInterventionRetryCount(request.getInterventionRetryCount() + 1);
                request.setInterventionQueryLevel(Math.min(request.getInterventionQueryLevel() + 1, 2));
                anyNeedTransform = true;
            }
        }

        if (anyNeedTransform) {
            log.info("知识侧-重排层节点-前置检查：需要重试的类型[症状={},诊断={},干预={}]，路由到查询变换层",
                    request.isSymptomNeedTransform(), request.isDiagnosisNeedTransform(),
                    request.isInterventionNeedTransform());
            return ROUTING_RETRY_ALL;
        }

        log.info("知识侧-重排层节点-前置检查：所有空库重试次数已耗尽，已应用兜底内容，继续重排有数据的库");
        return null;
    }

    /**
     * 判断分片ID映射是否为空
     *
     * @param sliceIds 分片ID与相似度的键值对，可能为null
     * @return {@code true}表示为空（null或空Map），{@code false}表示有数据
     */
    private boolean isSliceIdsEmpty(Map<Long, Double> sliceIds) {
        return sliceIds == null || sliceIds.isEmpty();
    }

    // ==================== 阶段2：重排 ====================

    /**
     * 重排输出结果
     * <p>封装重排阶段的三类知识库切片列表及对应的参考提示词</p>
     *
     * @param symptomSliceList         症状库重排后的切片列表
     * @param diagnosisSliceList       诊断标准库重排后的切片列表
     * @param interventionSliceList    干预方案库重排后的切片列表
     * @param symptomReferencePrompt   症状库参考提示词
     * @param diagnosisReferencePrompt 诊断标准库参考提示词
     * @param interventionReferencePrompt 干预方案库参考提示词
     */
    private record RerankOutput(
            List<KnowledgeSliceItem> symptomSliceList,
            List<KnowledgeSliceItem> diagnosisSliceList,
            List<KnowledgeSliceItem> interventionSliceList,
            String symptomReferencePrompt,
            String diagnosisReferencePrompt,
            String interventionReferencePrompt
    ) {}

    /**
     * 执行重排逻辑
     * <p>对有数据的库执行完整的重排流程：</p>
     * <ol>
     *   <li>根据sliceIds从数据库查询知识文档</li>
     *   <li>构建用户提示词，包含用户信息和候选切片</li>
     *   <li>调用重排模型对候选切片进行相关性打分</li>
     *   <li>基于模型分和向量分加权计算综合分</li>
     *   <li>按综合分过滤低于阈值的文档，取TopN截断</li>
     *   <li>查询关联文件信息，构建{@link KnowledgeSliceItem}列表</li>
     *   <li>生成各库的参考提示词</li>
     * </ol>
     *
     * @param request 知识匹配请求，包含sliceIds和用户信息
     * @return 重排输出结果，包含过滤后的切片列表和参考提示词
     * @throws Exception 当模型调用失败时抛出
     */
    private RerankOutput executeRerank(KnowledgeMatchRequest request) throws Exception {
        Map<Long, Double> symptomSliceIdMap = request.getSymptomSliceIds() != null ? request.getSymptomSliceIds() : Map.of();
        Map<Long, Double> diagnosisSliceIdMap = request.getDiagnosisSliceIds() != null ? request.getDiagnosisSliceIds() : Map.of();
        Map<Long, Double> interventionSliceIdMap = request.getInterventionSliceIds() != null ? request.getInterventionSliceIds() : Map.of();

        List<Long> symptomSliceIds = new ArrayList<>(symptomSliceIdMap.keySet());
        List<Long> diagnosisSliceIds = new ArrayList<>(diagnosisSliceIdMap.keySet());
        List<Long> interventionSliceIds = new ArrayList<>(interventionSliceIdMap.keySet());

        List<KnowledgeDocument> symptomDocs = symptomSliceIds.isEmpty()
                ? List.of()
                : knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<>(KnowledgeDocument.class)
                        .in(KnowledgeDocument::getSliceId, symptomSliceIds));
        List<KnowledgeDocument> diagnosisDocs = diagnosisSliceIds.isEmpty()
                ? List.of()
                : knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<>(KnowledgeDocument.class)
                        .in(KnowledgeDocument::getSliceId, diagnosisSliceIds));
        List<KnowledgeDocument> interventionDocs = interventionSliceIds.isEmpty()
                ? List.of()
                : knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<>(KnowledgeDocument.class)
                        .in(KnowledgeDocument::getSliceId, interventionSliceIds));

        boolean allDocsEmpty = symptomDocs.isEmpty() && diagnosisDocs.isEmpty() && interventionDocs.isEmpty();

        if (allDocsEmpty) {
            log.warn("知识侧-重排层节点-重排阶段：三类知识文档全部为空，跳过重排");
            return new RerankOutput(List.of(), List.of(), List.of(), "", "", "");
        }

        String userPrompt = buildUserPrompt(request,
                symptomDocs, diagnosisDocs, interventionDocs,
                symptomSliceIdMap, diagnosisSliceIdMap, interventionSliceIdMap);
        log.info("知识侧-重排层节点-构建用户提示词完成");

        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfig(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));
        RerankLayerModel.RerankLayerResult rerankResult = rerankLayerModel.callForResult(chatModel, userPrompt, aiNodeConfig);

        if (rerankResult == null) {
            log.error("知识侧-重排层节点-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.RERANK_LAYER_RESULT_NOT_EXIST);
        }

        Map<Long, Double> symptomModelScores = rerankResult.getSymptomScores();
        Map<Long, Double> diagnosisModelScores = rerankResult.getDiagnosisScores();
        Map<Long, Double> interventionModelScores = rerankResult.getInterventionScores();

        List<KnowledgeDocument> filteredSymptomDocs = rerankFilterDocs(
                symptomDocs, symptomModelScores, symptomSliceIdMap, maxSymptomCount, minScoreThreshold);
        List<KnowledgeDocument> filteredDiagnosisDocs = rerankFilterDocs(
                diagnosisDocs, diagnosisModelScores, diagnosisSliceIdMap, maxDiagnosisCount, minScoreThreshold);
        List<KnowledgeDocument> filteredInterventionDocs = rerankFilterDocs(
                interventionDocs, interventionModelScores, interventionSliceIdMap, maxInterventionCount, minScoreThreshold);

        List<Long> fileIds = Stream.of(filteredSymptomDocs, filteredDiagnosisDocs, filteredInterventionDocs)
                .flatMap(Collection::stream)
                .map(KnowledgeDocument::getFileId)
                .toList();

        Map<Long, InfraFile> fileIdToInfraFileMap = Map.of();
        if (!fileIds.isEmpty()) {
            List<InfraFile> infraFileList = infraFileMapper.selectList(new LambdaQueryWrapper<>(InfraFile.class)
                    .in(InfraFile::getId, fileIds));
            fileIdToInfraFileMap = infraFileList.stream()
                    .collect(Collectors.toMap(InfraFile::getId, f -> f));
        }

        Map<Long, InfraFile> finalFileIdToInfraFileMap = fileIdToInfraFileMap;
        List<KnowledgeSliceItem> symptomSliceList = filteredSymptomDocs.stream()
                .map(doc -> buildKnowledgeSliceItem(doc, finalFileIdToInfraFileMap,
                        KnowledgeRetrieveResult.KNOWLEDGE_TYPE_SYMPTOM, symptomModelScores, symptomSliceIdMap))
                .toList();
        List<KnowledgeSliceItem> diagnosisSliceList = filteredDiagnosisDocs.stream()
                .map(doc -> buildKnowledgeSliceItem(doc, finalFileIdToInfraFileMap,
                        KnowledgeRetrieveResult.KNOWLEDGE_TYPE_DIAGNOSIS, diagnosisModelScores, diagnosisSliceIdMap))
                .toList();
        List<KnowledgeSliceItem> interventionSliceList = filteredInterventionDocs.stream()
                .map(doc -> buildKnowledgeSliceItem(doc, finalFileIdToInfraFileMap,
                        KnowledgeRetrieveResult.KNOWLEDGE_TYPE_INTERVENTION, interventionModelScores, interventionSliceIdMap))
                .toList();

        String symptomReferencePrompt = buildReferencePrompt("症状参考",
                "以下是从症状知识库中检索到的与用户症状表现相关的参考资料，描述了各类心理症状的临床表现、严重程度标准等，请据此辅助判断用户当前的症状特征：",
                filteredSymptomDocs);
        String diagnosisReferencePrompt = buildReferencePrompt("诊断标准参考",
                "以下是从诊断标准知识库中检索到的与用户症状及情绪状态相关的诊断条目，描述了心理疾病的诊断标准和分类依据，请据此辅助判断用户的心理状态是否符合某类诊断：",
                filteredDiagnosisDocs);
        String interventionReferencePrompt = buildReferencePrompt("干预方案参考",
                "以下是从干预方案知识库中检索到的与用户核心诉求相关的干预方法，描述了针对性的心理干预策略、调节技巧和治疗建议，请据此为用户提供合适的干预方案：",
                filteredInterventionDocs);

        log.info("知识侧-重排层节点-重排完成，症状{}条，诊断{}条，干预{}条",
                symptomSliceList.size(), diagnosisSliceList.size(), interventionSliceList.size());

        return new RerankOutput(symptomSliceList, diagnosisSliceList, interventionSliceList,
                symptomReferencePrompt, diagnosisReferencePrompt, interventionReferencePrompt);
    }

    // ==================== 阶段3：后置检查 ====================

    /**
     * 后置检查：检查重排后结果是否为空，决定重试或兜底
     * <ul>
     *   <li>全部有数据 → 设置结果 + end</li>
     *   <li>重排后为空 + 对应类型有重试次数 → 设置needTransform=true，路由到查询变换层</li>
     *   <li>重排后为空 + 对应类型无重试次数 → 兜底内容 + end</li>
     * </ul>
     */
    private String postRerankCheck(KnowledgeMatchRequest request,
                                   KnowledgeRetrieveResult result,
                                   RerankOutput output) {
        boolean symptomRerankEmpty = output.symptomSliceList().isEmpty();
        boolean diagnosisRerankEmpty = output.diagnosisSliceList().isEmpty();
        boolean interventionRerankEmpty = output.interventionSliceList().isEmpty();
        int postEmptyCount = (symptomRerankEmpty ? 1 : 0) + (diagnosisRerankEmpty ? 1 : 0) + (interventionRerankEmpty ? 1 : 0);

        if (postEmptyCount == 0) {
            setResultFromRerank(result, output);
            return ROUTING_END;
        }

        log.info("知识侧-重排层节点-后置检查：重排后存在空结果，空库数={}，重试次数[症状={},诊断={},干预={}]",
                postEmptyCount, request.getSymptomRetryCount(), request.getDiagnosisRetryCount(),
                request.getInterventionRetryCount());

        boolean anyNeedTransform = false;

        if (symptomRerankEmpty) {
            if (request.getSymptomRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("知识侧-重排层节点-后置检查：症状库重试次数耗尽，使用兜底内容");
                applyFallbackContent(result, true, false, false);
            } else {
                request.setSymptomNeedTransform(true);
                request.setSymptomRetryCount(request.getSymptomRetryCount() + 1);
                request.setSymptomQueryLevel(Math.min(request.getSymptomQueryLevel() + 1, 2));
                anyNeedTransform = true;
            }
        }

        if (diagnosisRerankEmpty) {
            if (request.getDiagnosisRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("知识侧-重排层节点-后置检查：诊断标准库重试次数耗尽，使用兜底内容");
                applyFallbackContent(result, false, true, false);
            } else {
                request.setDiagnosisNeedTransform(true);
                request.setDiagnosisRetryCount(request.getDiagnosisRetryCount() + 1);
                request.setDiagnosisQueryLevel(Math.min(request.getDiagnosisQueryLevel() + 1, 2));
                anyNeedTransform = true;
            }
        }

        if (interventionRerankEmpty) {
            if (request.getInterventionRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("知识侧-重排层节点-后置检查：干预方案库重试次数耗尽，使用兜底内容");
                applyFallbackContent(result, false, false, true);
            } else {
                request.setInterventionNeedTransform(true);
                request.setInterventionRetryCount(request.getInterventionRetryCount() + 1);
                request.setInterventionQueryLevel(Math.min(request.getInterventionQueryLevel() + 1, 2));
                anyNeedTransform = true;
            }
        }

        setResultFromRerank(result, output);

        if (anyNeedTransform) {
            log.info("知识侧-重排层节点-后置检查：需要重试的类型[症状={},诊断={},干预={}]，路由到查询变换层",
                    request.isSymptomNeedTransform(), request.isDiagnosisNeedTransform(),
                    request.isInterventionNeedTransform());
            return ROUTING_RETRY_ALL;
        }

        return ROUTING_END;
    }

    /**
     * 将重排结果设置到KnowledgeRetrieveResult中
     * <p>仅设置非空的字段，保留兜底内容不被覆盖</p>
     */
    private void setResultFromRerank(KnowledgeRetrieveResult result, RerankOutput output) {
        if (!output.symptomSliceList().isEmpty()) {
            result.setSymptomSliceList(output.symptomSliceList());
            result.setSymptomReferencePrompt(output.symptomReferencePrompt());
        }
        if (!output.diagnosisSliceList().isEmpty()) {
            result.setDiagnosisSliceList(output.diagnosisSliceList());
            result.setDiagnosisReferencePrompt(output.diagnosisReferencePrompt());
        }
        if (!output.interventionSliceList().isEmpty()) {
            result.setInterventionSliceList(output.interventionSliceList());
            result.setInterventionReferencePrompt(output.interventionReferencePrompt());
        }
    }

    // ==================== 兜底内容 ====================

    private void applyFallbackContent(KnowledgeRetrieveResult result,
                                      boolean symptomEmpty,
                                      boolean diagnosisEmpty,
                                      boolean interventionEmpty) {
        if (symptomEmpty) {
            result.setSymptomSliceList(List.of());
            result.setSymptomReferencePrompt(FALLBACK_SYMPTOM_PROMPT);
        }
        if (diagnosisEmpty) {
            result.setDiagnosisSliceList(List.of());
            result.setDiagnosisReferencePrompt(FALLBACK_DIAGNOSIS_PROMPT);
        }
        if (interventionEmpty) {
            result.setInterventionSliceList(List.of());
            result.setInterventionReferencePrompt(FALLBACK_INTERVENTION_PROMPT);
        }

        log.info("知识侧-重排层节点-兜底内容已应用，症状={}，诊断={}，干预={}",
                symptomEmpty, diagnosisEmpty, interventionEmpty);
    }

    // ==================== 重排过滤与构建 ====================

    /**
     * 重排过滤文档
     * <p>基于模型分和向量分的加权综合分进行过滤和截断：</p>
     * <ol>
     *   <li>计算每个文档的综合分 = 模型分 × {@value #modelScoreWeight} + 向量分 × {@value #vectorScoreWeight}</li>
     *   <li>按综合分降序排列</li>
     *   <li>过滤低于{@code threshold}的文档</li>
     *   <li>截断至{@code maxCount}条</li>
     * </ol>
     * <p>若过滤后无结果，启用向量分兜底：仅按向量分排序，取前2条（不超过maxCount）</p>
     *
     * @param docs           候选知识文档列表
     * @param modelScoreMap  模型相关性分值映射（sliceId → 分值），可能为null
     * @param vectorScoreMap 向量相似度分值映射（sliceId → 分值）
     * @param maxCount       最大保留条数
     * @param threshold      最小综合分阈值
     * @return 过滤截断后的文档列表
     */
    private List<KnowledgeDocument> rerankFilterDocs(List<KnowledgeDocument> docs,
                                                     Map<Long, Double> modelScoreMap,
                                                     Map<Long, Double> vectorScoreMap,
                                                     int maxCount,
                                                     double threshold) {
        if (docs.isEmpty()) {
            return List.of();
        }

        List<ScoredDoc> scoredList = docs.stream()
                .map(doc -> {
                    double modelScore = modelScoreMap != null ? modelScoreMap.getOrDefault(doc.getSliceId(), 0.0) : 0.0;
                    double vecScore = vectorScoreMap.getOrDefault(doc.getSliceId(), 0.0);
                    double finalScore = modelScore * modelScoreWeight + vecScore * vectorScoreWeight;
                    return new ScoredDoc(doc, finalScore);
                })
                .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
                .filter(s -> s.score() >= threshold)
                .limit(maxCount)
                .toList();

        if (scoredList.isEmpty()) {
            log.warn("重排过滤后无结果（阈值={}），启用向量分兜底", threshold);
            scoredList = docs.stream()
                    .map(doc -> new ScoredDoc(doc, vectorScoreMap.getOrDefault(doc.getSliceId(), 0.0)))
                    .sorted(Comparator.comparingInt((ScoredDoc s) -> 0).thenComparingDouble(ScoredDoc::score).reversed())
                    .limit(Math.min(2, maxCount))
                    .toList();
        }
        return scoredList.stream().map(ScoredDoc::doc).toList();
    }

    /**
     * 构建参考提示词
     * <p>将重排后的知识文档格式化为结构化的参考提示词，供下游诊断模块使用。
     * 格式为：标题 + 描述 + 逐条内容。</p>
     *
     * @param title       参考提示词标题（如"症状参考"）
     * @param description 参考提示词描述说明
     * @param docs        重排后的知识文档列表
     * @return 格式化后的参考提示词，若文档列表为空则返回空字符串
     */
    private String buildReferencePrompt(String title, String description, List<KnowledgeDocument> docs) {
        if (docs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(title).append("】\n");
        sb.append(description).append("\n\n");
        for (int i = 0; i < docs.size(); i++) {
            KnowledgeDocument doc = docs.get(i);
            sb.append("第").append(i + 1).append("条：\n");
            sb.append(doc.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 构建重排模型的用户提示词
     * <p>将用户信息和候选切片组装为结构化提示词，包含：</p>
     * <ul>
     *   <li>用户信息：标准症状、主导情绪、核心诉求及各维度的筛选依据</li>
     *   <li>候选切片：三类知识库的切片ID、向量相似度和内容</li>
     *   <li>打分指引：症状匹配度、诉求匹配度、向量相似度三个维度的评分标准</li>
     * </ul>
     *
     * @param request               知识匹配请求
     * @param symptomDocs           症状库候选文档
     * @param diagnosisDocs         诊断标准库候选文档
     * @param interventionDocs      干预方案库候选文档
     * @param symptomSliceIdMap     症状库sliceId与向量相似度映射
     * @param diagnosisSliceIdMap   诊断标准库sliceId与向量相似度映射
     * @param interventionSliceIdMap 干预方案库sliceId与向量相似度映射
     * @return 构建完成的用户提示词
     */
    private String buildUserPrompt(KnowledgeMatchRequest request,
                                   List<KnowledgeDocument> symptomDocs,
                                   List<KnowledgeDocument> diagnosisDocs,
                                   List<KnowledgeDocument> interventionDocs,
                                   Map<Long, Double> symptomSliceIdMap,
                                   Map<Long, Double> diagnosisSliceIdMap,
                                   Map<Long, Double> interventionSliceIdMap) {
        StringBuilder sb = new StringBuilder();

        sb.append("以下是向量检索召回的候选知识切片，请结合用户信息进行二次语义筛选。\n\n");

        sb.append("========== 用户信息 ==========\n\n");

        List<String> standardSymptoms = request.getStandardSymptoms();
        if (standardSymptoms != null && !standardSymptoms.isEmpty()) {
            sb.append("【标准症状】由上游模块对用户描述进行归一化提取得到，代表用户当前呈现的核心症状表现：\n");
            for (String symptom : standardSymptoms) {
                sb.append("  - ").append(symptom).append("\n");
            }
            sb.append("→ 筛选依据：症状库和诊断标准库的切片内容应与上述症状有直接或间接的语义关联，命中越多相关性越高。\n\n");
        }

        String coreEmotion = request.getCoreEmotion();
        if (coreEmotion != null && !coreEmotion.isBlank()) {
            sb.append("【主导情绪】由情绪识别模块输出，代表用户当前最突出的情绪状态：\n");
            sb.append("  ").append(coreEmotion).append("\n");
            sb.append("→ 筛选依据：诊断标准库的切片应与该情绪状态对应的诊断条目相关。\n\n");
        }

        String coreAppeal = request.getCoreAppeal();
        if (coreAppeal != null && !coreAppeal.isBlank()) {
            sb.append("【核心诉求】由上游模块从用户对话中提取，代表用户最希望解决的问题或获得的帮助：\n");
            sb.append("  ").append(coreAppeal).append("\n");
            sb.append("→ 筛选依据：干预方案库的切片应与该核心诉求有语义关联，提供针对性的干预方法或调节策略。\n\n");
        }

        sb.append("========== 候选切片 ==========\n\n");

        sb.append("【症状库候选切片】共").append(symptomDocs.size()).append("条，描述各类心理症状的表现、诊断标准等，用于校验用户症状的匹配程度：\n");
        for (KnowledgeDocument doc : symptomDocs) {
            Double score = symptomSliceIdMap.get(doc.getSliceId());
            sb.append("  - 切片ID: ").append(doc.getSliceId());
            if (score != null) {
                sb.append(" | 向量相似度: ").append(String.format("%.4f", score));
            }
            sb.append("\n    内容: ").append(doc.getContent()).append("\n\n");
        }

        sb.append("【诊断标准库候选切片】共").append(diagnosisDocs.size()).append("条，描述心理疾病的诊断条目和标准，用于辅助判断用户的心理状态：\n");
        for (KnowledgeDocument doc : diagnosisDocs) {
            Double score = diagnosisSliceIdMap.get(doc.getSliceId());
            sb.append("  - 切片ID: ").append(doc.getSliceId());
            if (score != null) {
                sb.append(" | 向量相似度: ").append(String.format("%.4f", score));
            }
            sb.append("\n    内容: ").append(doc.getContent()).append("\n\n");
        }

        sb.append("【干预方案库候选切片】共").append(interventionDocs.size()).append("条，描述心理干预方法、调节策略和治疗建议，用于匹配用户的核心诉求：\n");
        for (KnowledgeDocument doc : interventionDocs) {
            Double score = interventionSliceIdMap.get(doc.getSliceId());
            sb.append("  - 切片ID: ").append(doc.getSliceId());
            if (score != null) {
                sb.append(" | 向量相似度: ").append(String.format("%.4f", score));
            }
            sb.append("\n    内容: ").append(doc.getContent()).append("\n\n");
        }

        sb.append("========== 打分指引 ==========\n\n");
        sb.append("请综合以下维度为每个候选切片评估0~1的相关性分值：\n");
        sb.append("1. 症状匹配度（核心维度）：切片内容与用户标准症状的重合程度，命中症状关键词越多、语义越贴近，分值越高；\n");
        sb.append("2. 诉求匹配度（干预库核心维度）：干预方案切片与用户核心诉求的语义关联程度，能否提供针对性的干预方法；\n");
        sb.append("3. 向量相似度（参考维度）：上方标注的向量相似度分数，分数越高说明语义大方向越相关，但需结合上述业务维度综合判断，避免仅依赖分数。\n\n");
        sb.append("请严格按照系统提示中的打分约束和输出格式，为每个候选切片输出相关性分值。\n");

        return sb.toString();
    }

    /**
     * 构建知识切片项
     * <p>将知识文档和关联文件信息组装为{@link KnowledgeSliceItem}，
     * 计算加权综合分作为相似度分数。</p>
     *
     * @param doc                  知识文档
     * @param fileIdToInfraFileMap 文件ID到文件信息的映射
     * @param knowledgeType        知识类型（症状/诊断/干预）
     * @param modelScoreMap        模型相关性分值映射
     * @param vectorScoreMap       向量相似度分值映射
     * @return 构建完成的知识切片项
     */
    private KnowledgeSliceItem buildKnowledgeSliceItem(KnowledgeDocument doc,
                                                       Map<Long, InfraFile> fileIdToInfraFileMap,
                                                       String knowledgeType,
                                                       Map<Long, Double> modelScoreMap,
                                                       Map<Long, Double> vectorScoreMap) {
        InfraFile infraFile = fileIdToInfraFileMap.get(doc.getFileId());

        double modelScore = modelScoreMap != null ? modelScoreMap.getOrDefault(doc.getSliceId(), 0.0) : 0.0;
        double vecScore = vectorScoreMap.getOrDefault(doc.getSliceId(), 0.0);
        double finalScore = modelScore * modelScoreWeight + vecScore * vectorScoreWeight;

        return KnowledgeSliceItem.builder()
                .sliceId(String.valueOf(doc.getSliceId()))
                .documentId(String.valueOf(doc.getId()))
                .documentTitle(infraFile != null ? infraFile.getOriginalName() : "")
                .chunkLevel1Idx(doc.getChunkLevel1Idx())
                .chunkLevel2Idx(doc.getChunkLevel2Idx())
                .content(doc.getContent())
                .similarityScore(BigDecimal.valueOf(finalScore))
                .knowledgeType(knowledgeType)
                .source(infraFile != null ? infraFile.getSource() : "")
                .build();
    }

    /**
     * 带分值的文档记录，用于重排过滤时的排序和过滤
     *
     * @param doc   知识文档
     * @param score 综合分值
     */
    private record ScoredDoc(KnowledgeDocument doc, double score) {}
}