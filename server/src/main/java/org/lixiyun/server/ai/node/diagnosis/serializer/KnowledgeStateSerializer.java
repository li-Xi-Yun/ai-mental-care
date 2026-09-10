package org.lixiyun.server.ai.node.diagnosis.serializer;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.TypeMapper;
import com.alibaba.cloud.ai.graph.state.AgentStateFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeMatchRequest;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeSliceItem;

/**
 * KnowledgeGraph 专用状态序列化器。
 * <p>
 * 解决 spring-ai-alibaba-graph 默认 {@code DEFAULT_JACKSON_SERIALIZER} 在并行分支触发
 * {@code cloneState()} 时，因 Jackson 反序列化 {@code Map<String, Object>} 的 value 声明为
 * {@code Object} 导致自定义 POJO 被擦除为 {@code LinkedHashMap} 的类型丢失问题。
 * <p>
 * 注册类型范围：
 * <ul>
 *   <li>知识侧：{@code KnowledgeMatchRequest}、{@code KnowledgeRetrieveResult}、{@code KnowledgeSliceItem}</li>
 * </ul>
 *
 * @see SpringAIJacksonStateSerializer
 * @see TypeMapper.Reference
 */
public class KnowledgeStateSerializer extends SpringAIJacksonStateSerializer {

    /**
     * @param stateFactory 状态工厂，通常传入 {@code OverAllState::new}
     */
    public KnowledgeStateSerializer(AgentStateFactory<OverAllState> stateFactory) {
        super(stateFactory);
        registerTypes();
    }

    /**
     * @param stateFactory  状态工厂，通常传入 {@code OverAllState::new}
     * @param objectMapper 自定义 Jackson ObjectMapper
     */
    public KnowledgeStateSerializer(AgentStateFactory<OverAllState> stateFactory, ObjectMapper objectMapper) {
        super(stateFactory, objectMapper);
        registerTypes();
    }

    private void registerTypes() {
        TypeMapper tm = this.typeMapper();

        tm.register(new TypeMapper.Reference<KnowledgeMatchRequest>(KnowledgeMatchRequest.class.getName()) {});
        tm.register(new TypeMapper.Reference<KnowledgeRetrieveResult>(KnowledgeRetrieveResult.class.getName()) {});
        tm.register(new TypeMapper.Reference<KnowledgeSliceItem>(KnowledgeSliceItem.class.getName()) {});
    }
}