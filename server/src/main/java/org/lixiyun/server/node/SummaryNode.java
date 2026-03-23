package org.lixiyun.server.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * @author lixiyun
 * @since 2026-03-19 12:23
 */
@Slf4j
@Builder
public class SummaryNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "summaryNode";

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        log.info("汇总节点开始执行");
        Optional<Map<String, Object>> metadata = config.metadata();
        if(metadata.isPresent()){
            Map<String, Object> map = metadata.get();
            ArrayList<Message> streamOutput = (ArrayList<Message>) map.get(GraphConstant.STREAM_RESULT);
            ArrayList<Message> conversationMessages = (ArrayList<Message>) map.get(GraphConstant.CONVERSATION_MESSAGES);
            conversationMessages.addAll(streamOutput);
//            streamOutput.clear();
            log.debug("汇总节点：metadata:流式输出：{}", streamOutput);

            return Map.of(GraphConstant.MESSAGES, streamOutput);
        }

        return Map.of();
    }
}
