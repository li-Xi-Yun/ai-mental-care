package org.lixiyun.server.ai.node.diagnosis.knowledge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
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
import org.lixiyun.server.ai.model.ChatModelFactory;
import org.lixiyun.server.ai.model.diagnosis.knowlegde.RerankLayerModel;
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
 * <p>对向量召回的粗结果做二次语义校验，基于模型相关性分与向量相似度分加权打分，
 * 过滤低质内容、去重截断，最终输出高相关的核心素材。</p>
 *
 * <h3>核心流程</h3>
 * <pre>向量召回粗结果 → 构建提示词 → 模型打分 → 加权综合分 → 阈值过滤 → TopN截断 → 封装输出</pre>
 *
 * <h3>综合分公式</h3>
 * <pre>最终综合分 = 模型相关性分 × 0.7 + 向量相似度分 × 0.3</pre>
 *
 * <h3>TopN截断规则</h3>
 * <ul>
 *   <li>症状库：最多保留3条</li>
 *   <li>诊断标准库：最多保留2条</li>
 *   <li>干预方案库：最多保留4条</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-08-12 13:05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankLayerNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "rerankLayerNode";

    /** 模型分值权重 */
    private static final double modelScoreWeight = 0.7;
    /** 向量分值权重 */
    private static final double vectorScoreWeight = 0.3;
    /** 最小综合分阈值，低于该阈值的切片将被过滤 */
    private static final double minScoreThreshold = 0.5;
    /** 症状库最大保留条数 */
    private static final int maxSymptomCount = 3;
    /** 诊断标准库最大保留条数 */
    private static final int maxDiagnosisCount = 2;
    /** 干预方案库最大保留条数 */
    private static final int maxInterventionCount = 4;

    private final RerankLayerModel rerankLayerModel;
    private final ChatModelFactory chatModelFactory;
    private final ChatModel chatModel = chatModelFactory.getDeepSeekChatModel();
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final InfraFileMapper infraFileMapper;

    /**
     * 重排层节点执行入口
     * <p>从状态图中读取向量召回的切片ID与相似度映射，查询知识文档与文件元数据，
     * 调用重排层模型对候选切片进行语义打分，再通过加权综合分完成过滤、排序、截断，
     * 最终将结果封装为 {@link KnowledgeRetrieveResult} 写回状态图。</p>
     *
     * <h4>执行步骤</h4>
     * <ol>
     *   <li>从状态图获取 {@link KnowledgeMatchRequest}，提取三类切片ID→向量相似度映射</li>
     *   <li>根据切片ID批量查询 {@link KnowledgeDocument}，校验非空</li>
     *   <li>根据文档关联的 fileId 批量查询 {@link InfraFile}，构建 fileId→InfraFile 映射</li>
     *   <li>构建用户提示词（含用户信息、候选切片、打分指引），调用重排层模型获取相关性分值</li>
     *   <li>对三类文档分别执行 {@link #rerankFilterDocs} 加权打分、阈值过滤、TopN截断</li>
     *   <li>将过滤后的文档封装为 {@link KnowledgeSliceItem} 列表，拼接参考提示词</li>
     *   <li>构建 {@link KnowledgeRetrieveResult} 返回状态图</li>
     * </ol>
     *
     * @param state  全局状态图，包含上游节点写入的 KnowledgeMatchRequest
     * @param config 运行时配置
     * @return 包含 {@link KnowledgeRetrieveResult#NAME} → {@link KnowledgeRetrieveResult} 的映射
     * @throws BusinessException 当知识匹配请求为空、知识文档为空、文件元数据为空或模型输出解析失败时抛出
     */
    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("知识侧-重排层节点-开始");

        Optional<KnowledgeMatchRequest> knowledgeMatchRequestOpt = state.value(KnowledgeMatchRequest.NAME);
        if (knowledgeMatchRequestOpt.isEmpty()) {
            log.error("知识侧-重排层节点-知识匹配请求为空");
            throw new BusinessException(ConversationExceptionEnum.KNOWLEDGE_MATCH_REQUEST_NOT_EXIST);
        }
        KnowledgeMatchRequest knowledgeMatchRequest = knowledgeMatchRequestOpt.get();

        Map<Long, Double> symptomSliceIdMap = knowledgeMatchRequest.getSymptomSliceIds();
        Map<Long, Double> diagnosisSliceIdMap = knowledgeMatchRequest.getDiagnosisSliceIds();
        Map<Long, Double> interventionSliceIdMap = knowledgeMatchRequest.getInterventionSliceIds();

        List<Long> symptomSliceIds = new ArrayList<>(symptomSliceIdMap.keySet());
        List<Long> diagnosisSliceIds = new ArrayList<>(diagnosisSliceIdMap.keySet());
        List<Long> interventionSliceIds = new ArrayList<>(interventionSliceIdMap.keySet());

        List<KnowledgeDocument> symptomKnowledgeDocumentList = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<>(KnowledgeDocument.class)
                .in(KnowledgeDocument::getSliceId, symptomSliceIds)
        );
        List<KnowledgeDocument> interventionKnowledgeDocumentList = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<>(KnowledgeDocument.class)
                .in(KnowledgeDocument::getSliceId, interventionSliceIds)
        );
        List<KnowledgeDocument> diagnosisKnowledgeDocumentList = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<>(KnowledgeDocument.class)
                .in(KnowledgeDocument::getSliceId, diagnosisSliceIds)
        );

        if (symptomKnowledgeDocumentList.isEmpty()) {
            log.warn("知识侧-重排层节点-症状知识文档为空");
            throw new BusinessException(ConversationExceptionEnum.SYMPTOM_KNOWLEDGE_DOCUMENT_LIST_EMPTY);
        }
        if (interventionKnowledgeDocumentList.isEmpty()) {
            log.warn("知识侧-重排层节点-干预知识文档为空");
            throw new BusinessException(ConversationExceptionEnum.INTERVENTION_KNOWLEDGE_DOCUMENT_LIST_EMPTY);
        }
        if (diagnosisKnowledgeDocumentList.isEmpty()) {
            log.warn("知识侧-重排层节点-诊断知识文档为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_KNOWLEDGE_DOCUMENT_LIST_EMPTY);
        }

        String userPrompt = buildUserPrompt(knowledgeMatchRequest,
                symptomKnowledgeDocumentList, diagnosisKnowledgeDocumentList, interventionKnowledgeDocumentList,
                symptomSliceIdMap, diagnosisSliceIdMap, interventionSliceIdMap);
        log.info("知识侧-重排层节点-构建用户提示词完成");

        RerankLayerModel.RerankLayerResult rerankResult = rerankLayerModel.callForResult(chatModel, userPrompt);

        if (rerankResult == null) {
            log.error("知识侧-重排层节点-模型输出解析失败");
            throw new BusinessException(ConversationExceptionEnum.RERANK_LAYER_RESULT_NOT_EXIST);
        }

        Map<Long, Double> symptomModelScores = rerankResult.getSymptomScores();
        Map<Long, Double> diagnosisModelScores = rerankResult.getDiagnosisScores();
        Map<Long, Double> interventionModelScores = rerankResult.getInterventionScores();

        List<KnowledgeDocument> filteredSymptomDocs = rerankFilterDocs(
                symptomKnowledgeDocumentList, symptomModelScores, symptomSliceIdMap, maxSymptomCount);
        List<KnowledgeDocument> filteredDiagnosisDocs = rerankFilterDocs(
                diagnosisKnowledgeDocumentList, diagnosisModelScores, diagnosisSliceIdMap, maxDiagnosisCount);
        List<KnowledgeDocument> filteredInterventionDocs = rerankFilterDocs(
                interventionKnowledgeDocumentList, interventionModelScores, interventionSliceIdMap, maxInterventionCount);

        List<Long> fileIds = Stream.of(filteredSymptomDocs, filteredDiagnosisDocs, filteredInterventionDocs)
                .flatMap(Collection::stream)
                .map(KnowledgeDocument::getFileId)
                .toList();

        List<InfraFile> infraFileList = infraFileMapper.selectList(new LambdaQueryWrapper<>(InfraFile.class)
                .in(InfraFile::getId, fileIds)
        );

        if (infraFileList.isEmpty()) {
            log.warn("知识侧-重排层节点-文件元数据为空");
            throw new BusinessException(ConversationExceptionEnum.INFRA_FILE_NOT_EXIST);
        }

        Map<Long, InfraFile> fileIdToInfraFileMap = infraFileList.stream()
                .collect(Collectors.toMap(InfraFile::getId, f -> f));

        List<KnowledgeSliceItem> symptomSliceList = filteredSymptomDocs.stream()
                .map(doc -> buildKnowledgeSliceItem(doc, fileIdToInfraFileMap, KnowledgeRetrieveResult.KNOWLEDGE_TYPE_SYMPTOM,
                        symptomModelScores, symptomSliceIdMap))
                .toList();
        List<KnowledgeSliceItem> diagnosisSliceList = filteredDiagnosisDocs.stream()
                .map(doc -> buildKnowledgeSliceItem(doc, fileIdToInfraFileMap, KnowledgeRetrieveResult.KNOWLEDGE_TYPE_DIAGNOSIS,
                        diagnosisModelScores, diagnosisSliceIdMap))
                .toList();
        List<KnowledgeSliceItem> interventionSliceList = filteredInterventionDocs.stream()
                .map(doc -> buildKnowledgeSliceItem(doc, fileIdToInfraFileMap, KnowledgeRetrieveResult.KNOWLEDGE_TYPE_INTERVENTION,
                        interventionModelScores, interventionSliceIdMap))
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

        KnowledgeRetrieveResult finalResult = KnowledgeRetrieveResult.builder()
                .symptomReferencePrompt(symptomReferencePrompt)
                .diagnosisReferencePrompt(diagnosisReferencePrompt)
                .interventionReferencePrompt(interventionReferencePrompt)
                .symptomSliceList(symptomSliceList)
                .diagnosisSliceList(diagnosisSliceList)
                .interventionSliceList(interventionSliceList)
                .build();

        log.info("知识侧-重排层节点-完成，症状{}条，诊断{}条，干预{}条",
                symptomSliceList.size(), diagnosisSliceList.size(), interventionSliceList.size());
        return Map.of(KnowledgeRetrieveResult.NAME, finalResult);
    }

    /**
     * 通用重排、打分、过滤、截断逻辑
     * <p>对候选文档计算加权综合分，按综合分降序排列后执行硬阈值过滤和TopN截断。
     * 若过滤后结果为空，则启用兜底逻辑：退回纯向量分排序，取前 min(2, maxCount) 条。</p>
     *
     * <h4>综合分公式</h4>
     * <pre>最终综合分 = 模型相关性分 × modelScoreWeight + 向量相似度分 × vectorScoreWeight</pre>
     *
     * @param docs           原始候选文档列表
     * @param modelScoreMap  模型输出的切片ID→相关性分值映射，可为null（兜底时视为0）
     * @param vectorScoreMap 向量召回的切片ID→相似度分值映射
     * @param maxCount       当前知识类型的最大保留条数
     * @return 经过加权打分、阈值过滤、TopN截断后的文档列表，按综合分降序排列
     */
    private List<KnowledgeDocument> rerankFilterDocs(List<KnowledgeDocument> docs,
                                                     Map<Long, Double> modelScoreMap,
                                                     Map<Long, Double> vectorScoreMap,
                                                     int maxCount) {
        List<ScoredDoc> scoredList = docs.stream()
                .map(doc -> {
                    double modelScore = modelScoreMap != null ? modelScoreMap.getOrDefault(doc.getSliceId(), 0.0) : 0.0;
                    double vecScore = vectorScoreMap.getOrDefault(doc.getSliceId(), 0.0);
                    double finalScore = modelScore * modelScoreWeight + vecScore * vectorScoreWeight;
                    return new ScoredDoc(doc, finalScore);
                })
                .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
                .filter(s -> s.score() >= minScoreThreshold)
                .limit(maxCount)
                .toList();

        if (scoredList.isEmpty()) {
            log.warn("重排过滤后无结果，启用向量分兜底");
            scoredList = docs.stream()
                    .map(doc -> new ScoredDoc(doc, vectorScoreMap.getOrDefault(doc.getSliceId(), 0.0)))
                    .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
                    .limit(Math.min(2, maxCount))
                    .toList();
        }
        return scoredList.stream().map(ScoredDoc::doc).toList();
    }

    /**
     * 构建下游模型输入用的参考提示词
     * <p>将过滤后的文档内容组装为带标题、用途说明和编号的结构化文本，
     * 供下游诊断/干预模型作为知识参考输入。</p>
     *
     * @param title       段落标题，如"症状参考"、"诊断标准参考"、"干预方案参考"
     * @param description 对该段参考资料的来源、内容特征和使用方式的说明
     * @param docs        过滤后的文档列表
     * @return 格式化后的参考提示词文本；若文档列表为空则返回空字符串
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
     * 构建重排层模型的用户提示词
     * <p>将用户信息（标准症状、主导情绪、核心诉求）与三类候选切片内容组装为结构化提示词，
     * 每条切片附带向量相似度分数，末尾附打分指引，引导模型输出0~1相关性分值。</p>
     *
     * <h4>提示词结构</h4>
     * <pre>
     * 用户信息（标准症状 + 主导情绪 + 核心诉求，各附筛选依据）
     * → 候选切片（症状库 + 诊断标准库 + 干预方案库，各附切片ID、向量相似度、内容）
     * → 打分指引（症状匹配度、诉求匹配度、向量相似度三个维度）
     * </pre>
     *
     * @param request               知识匹配请求，包含标准症状、主导情绪、核心诉求
     * @param symptomDocs           症状库候选文档列表
     * @param diagnosisDocs         诊断标准库候选文档列表
     * @param interventionDocs      干预方案库候选文档列表
     * @param symptomSliceIdMap     症状库切片ID→向量相似度映射
     * @param diagnosisSliceIdMap   诊断标准库切片ID→向量相似度映射
     * @param interventionSliceIdMap 干预方案库切片ID→向量相似度映射
     * @return 组装完成的用户提示词文本
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
     * 将知识文档与文件元数据封装为 {@link KnowledgeSliceItem}
     * <p>从文档关联的 InfraFile 获取文件名和来源信息，
     * 综合分按 {@code 模型分 × modelScoreWeight + 向量分 × vectorScoreWeight} 计算，
     * 写入 {@link KnowledgeSliceItem} 的 similarityScore 字段。</p>
     *
     * @param doc                  知识文档实体
     * @param fileIdToInfraFileMap fileId→InfraFile 映射，用于获取文档标题和来源
     * @param knowledgeType        知识类型标识（symptom/diagnosis/intervention）
     * @param modelScoreMap        模型输出的切片ID→相关性分值映射，可为null
     * @param vectorScoreMap       向量召回的切片ID→相似度分值映射
     * @return 封装完成的 KnowledgeSliceItem
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
     * 内部承载文档与对应综合分的记录
     *
     * @param doc   知识文档实体
     * @param score 加权综合分（模型分 × modelScoreWeight + 向量分 × vectorScoreWeight）
     */
    private record ScoredDoc(KnowledgeDocument doc, double score) {}

}