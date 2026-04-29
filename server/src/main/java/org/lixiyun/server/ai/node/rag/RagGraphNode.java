package org.lixiyun.server.ai.node.rag;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.SpringUtils;
import org.lixiyun.server.ai.node.graph.RagGraph;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RAG图节点
 * <p>使用该节点时，需要添加GraphConstant.RAG_RESULT到state中</p>
 * @author lixiyun
 * @since 2026-04-28 09:28
 */
@Slf4j
@Builder
public class RagGraphNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "ragGraphNode";

    private static final RagGraph ragGraph = SpringUtils.getBean(RagGraph.class);

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.debug("RagGraphNode开始执行");

        Optional<List<Message>> userMessageOpl = state.value(GraphConstant.MESSAGES);
        List<Message> historyMessages = userMessageOpl.orElseThrow(() -> {
            log.error("RagGraphNode-historyMessages:上下文不存在");
            return new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_FOUND);
        });

        String ragResult = ragGraph.executeRag(historyMessages);
        if(ragResult.isBlank()){
            log.debug("RagGraphNode执行失败，没有数据");
            return Map.of();
        }

        log.debug("RagGraphNode执行成功");
        return Map.of(GraphConstant.RAG_RESULT, ragResult);
    }
}
