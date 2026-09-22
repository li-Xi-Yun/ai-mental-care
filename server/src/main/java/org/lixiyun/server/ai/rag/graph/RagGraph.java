package org.lixiyun.server.ai.rag.graph;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.ai.rag.node.*;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * RAG图构建器
 * <p>负责构建检索增强生成(RAG)的工作流图，包含查询压缩、重写、扩展、检索、重排序和文档拼接等节点</p>
 *
 * @author lixiyun
 * @since 2026-04-27 13:04
 */
@Slf4j
@Component
@Deprecated
public class RagGraph {

    @Autowired
    @Qualifier("deepSeekChatModel")
    private ChatModel deepSeekChatModel;

    @Autowired
    @Qualifier("dashScopeChatModel")
    private ChatModel dashScopeChatModel;

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel ollamaChatModel;

    private static CompiledGraph ragGraph;

//    @PostConstruct
    public void init() {
        try {
            log.info("RAG图构建器初始化");
            ragGraph = buildGraph();
        } catch (Exception e) {
            log.error("RAG图构建器初始化失败", e);
        }
    }

    /**
     * 执行RAG图
     * @param historyMessageList 历史上下文信息
     * @param userInput 用户输入信息
     * @return 检索后返回的相关文本信息，可以使用（已处理好提示词）
     */
    public String executeRag(List<Message> historyMessageList, String userInput){
        if(historyMessageList == null){
            log.error("RagGraph-参数错误:历史上下文为空");
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_FOUND);
        }
        if(userInput.isBlank()){
            log.error("RagGraph-参数错误:用户输入为空");
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_NOT_FOUND);
        }

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor(CompressionQueryTransformerNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(RewriteQueryTransformerNode.NODE_NAME, ForkJoinPool.commonPool())
                .build();

        Map<String, Object> messages = Map.of(GraphConstant.MESSAGES, historyMessageList, GraphConstant.INPUT, userInput);

        OverAllState ragGraphState = ragGraph.invoke(messages, runnableConfig).orElse(null);
        if(ragGraphState == null){
            log.error("RagGraph-执行失败， ragGraphState为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        Optional<Object> ragResultOpl = ragGraphState.value(GraphConstant.RAG_RESULT);
        if(ragResultOpl.isEmpty()){
            log.error("RagGraph-执行失败， ragResultOpl为空");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        log.debug("RagGraph-执行成功，ragResultOpl:{}", ragResultOpl.get());
        return ragResultOpl.get().toString();
    }

    /**
     * 构建RAG工作流图
     * <p>按照以下流程构建RAG图：</p>
     * <ul>
     *   <li>CompressionQueryTransformerNode: 将用户查询压缩为核心关键词</li>
     *   <li>RewriteQueryTransformerNode: 将用户查询重写为专业术语</li>
     *   <li>MultiQueryExpanderNode: 基于压缩和重写结果扩展为多个查询</li>
     *   <li>RetrieverNode: 从向量数据库中检索相关文档</li>
     *   <li>RerankerNode: 对检索结果进行重排序和筛选</li>
     *   <li>ConcatenationJoinNode: 将文档拼接为完整上下文</li>
     * </ul>
     *
     * @return {@link CompiledGraph} 编译后的RAG图实例
     */
    private CompiledGraph buildGraph() {
        log.info("开始构建RAG图");

        // 定义状态策略
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(GraphConstant.MESSAGES, new AppendStrategy());
            keyStrategyMap.put(GraphConstant.INPUT, new ReplaceStrategy());
            keyStrategyMap.put(CompressionQueryTransformerNode.NODE_NAME, new ReplaceStrategy());
            keyStrategyMap.put(RewriteQueryTransformerNode.NODE_NAME, new ReplaceStrategy());
            keyStrategyMap.put(MultiQueryExpanderNode.NODE_NAME, new ReplaceStrategy());
            keyStrategyMap.put(RetrieverNode.NODE_NAME, new ReplaceStrategy());
            keyStrategyMap.put(RerankerNode.NODE_NAME, new ReplaceStrategy());
            keyStrategyMap.put(GraphConstant.RAG_RESULT, new ReplaceStrategy());
            return keyStrategyMap;
        };

        // 创建并配置各个RAG节点
        CompressionQueryTransformerNode compressionNode = CompressionQueryTransformerNode.builder().chatModel(dashScopeChatModel).build();
        RewriteQueryTransformerNode rewriteNode = RewriteQueryTransformerNode.builder().chatModel(dashScopeChatModel).build();
        MultiQueryExpanderNode expanderNode = MultiQueryExpanderNode.builder().chatModel(dashScopeChatModel).build();
        RetrieverNode retrieverNode = RetrieverNode.builder().build();
        RerankerNode rerankerNode = RerankerNode.builder().chatModel(dashScopeChatModel).build();
        ConcatenationJoinNode concatenationNode = ConcatenationJoinNode.builder().build();
        CompiledGraph compiledGraph = null;

        try {
            // 创建工作流图
            StateGraph workflow = new StateGraph(keyStrategyFactory)
                    .addNode("retry_node", AsyncNodeActionWithConfig.node_async((state, config) -> Map.of()))
                    .addNode(CompressionQueryTransformerNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(compressionNode))
                    .addNode(RewriteQueryTransformerNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(rewriteNode))
                    .addNode(MultiQueryExpanderNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(expanderNode))
                    .addNode(RetrieverNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(retrieverNode))
                    .addNode(RerankerNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(rerankerNode))
                    .addNode(ConcatenationJoinNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(concatenationNode));

            // 定义图的执行流程
            workflow.addEdge(StateGraph.START, "retry_node");
            workflow.addEdge("retry_node", CompressionQueryTransformerNode.NODE_NAME);
            workflow.addEdge("retry_node", RewriteQueryTransformerNode.NODE_NAME);
            workflow.addEdge(CompressionQueryTransformerNode.NODE_NAME, MultiQueryExpanderNode.NODE_NAME);
            workflow.addEdge(RewriteQueryTransformerNode.NODE_NAME, MultiQueryExpanderNode.NODE_NAME);
            workflow.addEdge(MultiQueryExpanderNode.NODE_NAME, RetrieverNode.NODE_NAME);

            // 向量检索节点后的条件跳转：重试回到开始节点，中断结束流程，否则进入重排序节点
            workflow.addConditionalEdges(RetrieverNode.NODE_NAME,
                    createRetryConditionEdge(),
                    Map.of("end", StateGraph.END,
                            "retry", "retry_node",
                            "process", RerankerNode.NODE_NAME
                    )
            );

            // 重排序节点后的条件跳转：重试回到开始节点，中断结束流程，否则进入文档拼接节点
            workflow.addConditionalEdges(
                    RerankerNode.NODE_NAME,
                    createRetryConditionEdge(),
                    Map.of("end", StateGraph.END,
                            "retry", "retry_node",
                            "process", ConcatenationJoinNode.NODE_NAME
                    )
            );

            workflow.addEdge(ConcatenationJoinNode.NODE_NAME, StateGraph.END);

            // 编译并返回图
            compiledGraph = workflow.compile();
            log.info("RAG图构建完成");
        } catch (GraphStateException e) {
            log.error("RAG图构建失败：{}", e.getMessage());
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        return compiledGraph;
    }

    /**
     * 创建重试条件边
     * <p>用于RAG流程中的重试和中断判断，返回一个条件边动作</p>
     *
     * @return {@link AsyncEdgeActionWithConfig} 条件边动作
     */
    private AsyncEdgeActionWithConfig createRetryConditionEdge() {
        return (state, config) -> {
            if (Boolean.TRUE.equals(config.context().get(GraphConstant.RAG_INTERRUPTED))) {
                log.debug("条件跳转：RAG流程已中断，结束流程");
                return CompletableFuture.completedFuture("end");
            }
            if (config.context().get("rag_retry_count") != null) {
                log.debug("条件跳转：触发重试，返回到重试节点");
                return CompletableFuture.completedFuture("retry");
            }
            return CompletableFuture.completedFuture("process");
        };
    }
}
