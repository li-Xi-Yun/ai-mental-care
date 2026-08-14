package org.lixiyun.server.ai.node.diagnosis;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.server.ai.node.diagnosis.graph.InputGraph;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 诊断流程-输入侧节点
 * <p>调用输入侧图，完成数据预清洗、消息结构化处理、情绪数据处理、历史诊断摘要聚合、语义归一化处理</p>
 *
 * @author lixiyun
 * @since 2026-08-14 11:41
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InputNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "inputNode";

    private final InputGraph inputGraph;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断流程-输入侧节点-开始");

        Optional<ConversationProcessContextBO> contextBOOpt = state.value(ConversationProcessContextBO.NAME);
        if (contextBOOpt.isEmpty()) {
            log.error("诊断流程-输入侧节点-会话上下文为空");
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_PROCESS_CONTEXT_NOT_EXIST);
        }

        ConversationProcessContextBO contextBO = contextBOOpt.get();
        InputResult inputResult = inputGraph.executeGraph(contextBO);

        if (inputResult.isInterrupted()) {
            log.info("诊断流程-输入侧节点-输入侧流程被中断，终止诊断流程");
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
        }

        log.info("诊断流程-输入侧节点-完成");
        return Map.of(InputResult.NAME, inputResult);
    }
}