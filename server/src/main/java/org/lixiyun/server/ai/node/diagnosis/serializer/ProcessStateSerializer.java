package org.lixiyun.server.ai.node.diagnosis.serializer;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.TypeMapper;
import com.alibaba.cloud.ai.graph.state.AgentStateFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisData;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisDataRequest;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.MessageEffectiveLevel;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.RoundEffectiveLevel;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.clean.SessionCleanResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization.SymptomNormalizeResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization.SymptomOriginalItem;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization.SymptomTerm;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.BaseInfo;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.EmotionDistributionItem;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.EmotionStatisticsResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.IntensityStat;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.PadDimension;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.Quantitative;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.SubEmotionDistributionItem;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.statistics.Trend;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.CoreInfoExtractResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.KeyEventItem;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.SymptomRawItem;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.DiagnosisSummary;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.EmotionTrend;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.HistoryDiagnosisSummaryResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.InterventionHistory;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.RiskPoints;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.SymptomEvolution;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionBasicInfo;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionDimensionTrend;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionPADStat;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.summary.trend.EmotionRatioStat;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeSliceItem;
import org.lixiyun.pojo.bo.conversation.state.GraphState;
import org.lixiyun.pojo.bo.conversation.state.InputGraphState;
import org.lixiyun.pojo.bo.conversation.state.KnowledgeGraphState;
import org.lixiyun.pojo.bo.conversation.state.ProcessGraphState;

/**
 * ProcessGraph 专用状态序列化器。
 * <p>
 * 解决 spring-ai-alibaba-graph 默认 {@code DEFAULT_JACKSON_SERIALIZER} 在并行分支触发
 * {@code cloneState()} 时，因 Jackson 反序列化 {@code Map<String, Object>} 的 value 声明为
 * {@code Object} 导致自定义 POJO 被擦除为 {@code LinkedHashMap} 的类型丢失问题。
 * <p>
 * 注册类型范围：
 * <ul>
 *   <li>处理侧：{@code DiagnosisDataRequest}、{@code DiagnosisData}</li>
 *   <li>输入侧：{@code InputResult} 及其全部子结构（因 {@code DiagnosisDataRequest} 字段引用）</li>
 *   <li>知识侧：{@code KnowledgeRetrieveResult}、{@code KnowledgeSliceItem}（因 {@code DiagnosisDataRequest} 字段引用）</li>
 * </ul>
 *
 * @see SpringAIJacksonStateSerializer
 * @see TypeMapper.Reference
 */
public class ProcessStateSerializer extends SpringAIJacksonStateSerializer {

    /**
     * @param stateFactory 状态工厂，通常传入 {@code OverAllState::new}
     */
    public ProcessStateSerializer(AgentStateFactory<OverAllState> stateFactory) {
        super(stateFactory);
        registerTypes();
    }

    /**
     * @param stateFactory  状态工厂，通常传入 {@code OverAllState::new}
     * @param objectMapper 自定义 Jackson ObjectMapper
     */
    public ProcessStateSerializer(AgentStateFactory<OverAllState> stateFactory, ObjectMapper objectMapper) {
        super(stateFactory, objectMapper);
        registerTypes();
    }

    private void registerTypes() {
        TypeMapper tm = this.typeMapper();

        tm.register(new TypeMapper.Reference<DiagnosisDataRequest>(DiagnosisDataRequest.class.getName()) {});
        tm.register(new TypeMapper.Reference<DiagnosisData>(DiagnosisData.class.getName()) {});

        tm.register(new TypeMapper.Reference<InputResult>(InputResult.class.getName()) {});
        tm.register(new TypeMapper.Reference<SessionCleanResult>(SessionCleanResult.class.getName()) {});
        tm.register(new TypeMapper.Reference<RoundEffectiveLevel>(RoundEffectiveLevel.class.getName()) {});
        tm.register(new TypeMapper.Reference<MessageEffectiveLevel>(MessageEffectiveLevel.class.getName()) {});
        tm.register(new TypeMapper.Reference<CoreInfoExtractResult>(CoreInfoExtractResult.class.getName()) {});
        tm.register(new TypeMapper.Reference<KeyEventItem>(KeyEventItem.class.getName()) {});
        tm.register(new TypeMapper.Reference<SymptomRawItem>(SymptomRawItem.class.getName()) {});
        tm.register(new TypeMapper.Reference<EmotionStatisticsResult>(EmotionStatisticsResult.class.getName()) {});
        tm.register(new TypeMapper.Reference<BaseInfo>(BaseInfo.class.getName()) {});
        tm.register(new TypeMapper.Reference<Quantitative>(Quantitative.class.getName()) {});
        tm.register(new TypeMapper.Reference<IntensityStat>(IntensityStat.class.getName()) {});
        tm.register(new TypeMapper.Reference<PadDimension>(PadDimension.class.getName()) {});
        tm.register(new TypeMapper.Reference<Trend>(Trend.class.getName()) {});
        tm.register(new TypeMapper.Reference<EmotionDistributionItem>(EmotionDistributionItem.class.getName()) {});
        tm.register(new TypeMapper.Reference<SubEmotionDistributionItem>(SubEmotionDistributionItem.class.getName()) {});
        tm.register(new TypeMapper.Reference<SymptomNormalizeResult>(SymptomNormalizeResult.class.getName()) {});
        tm.register(new TypeMapper.Reference<SymptomTerm>(SymptomTerm.class.getName()) {});
        tm.register(new TypeMapper.Reference<SymptomOriginalItem>(SymptomOriginalItem.class.getName()) {});
        tm.register(new TypeMapper.Reference<HistoryDiagnosisSummaryResult>(HistoryDiagnosisSummaryResult.class.getName()) {});
        tm.register(new TypeMapper.Reference<DiagnosisSummary>(DiagnosisSummary.class.getName()) {});
        tm.register(new TypeMapper.Reference<EmotionTrend>(EmotionTrend.class.getName()) {});
        tm.register(new TypeMapper.Reference<SymptomEvolution>(SymptomEvolution.class.getName()) {});
        tm.register(new TypeMapper.Reference<RiskPoints>(RiskPoints.class.getName()) {});
        tm.register(new TypeMapper.Reference<InterventionHistory>(InterventionHistory.class.getName()) {});
        tm.register(new TypeMapper.Reference<EmotionBasicInfo>(EmotionBasicInfo.class.getName()) {});
        tm.register(new TypeMapper.Reference<EmotionDimensionTrend>(EmotionDimensionTrend.class.getName()) {});
        tm.register(new TypeMapper.Reference<EmotionPADStat>(EmotionPADStat.class.getName()) {});
        tm.register(new TypeMapper.Reference<EmotionRatioStat>(EmotionRatioStat.class.getName()) {});

        tm.register(new TypeMapper.Reference<KnowledgeRetrieveResult>(KnowledgeRetrieveResult.class.getName()) {});
        tm.register(new TypeMapper.Reference<KnowledgeSliceItem>(KnowledgeSliceItem.class.getName()) {});

        tm.register(new TypeMapper.Reference<GraphState>(GraphState.class.getName()) {});
        tm.register(new TypeMapper.Reference<InputGraphState>(InputGraphState.class.getName()) {});
        tm.register(new TypeMapper.Reference<KnowledgeGraphState>(KnowledgeGraphState.class.getName()) {});
        tm.register(new TypeMapper.Reference<ProcessGraphState>(ProcessGraphState.class.getName()) {});
    }
}