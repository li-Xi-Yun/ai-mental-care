package org.lixiyun.server.service.impl;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.pojo.dto.chat.ChatDTO;
import org.lixiyun.pojo.entity.Conversation;
import org.lixiyun.pojo.entity.ConversationMemory;
import org.lixiyun.server.Saver.CustomMysqlSaver;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.infrastructure.agent.CommonServerAgent;
import org.lixiyun.server.infrastructure.storage.ConversationHistoryMessagesStorage;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.node.EmotionRecognitionNode;
import org.lixiyun.server.node.EmotionalDiagnosisNode;
import org.lixiyun.server.node.FinalAnswerNode;
import org.lixiyun.server.service.ChatService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author lixiyun
 * @since 2026-03-16 13:02
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
//    @Qualifier("ollamaChatModel")
    @Qualifier("deepSeekChatModel")
//    @Qualifier("dashScopeChatModel")
    private ChatModel deepSeekChatModel;

    @Autowired
//    @Qualifier("ollamaChatModel")
//    @Qualifier("deepSeekChatModel")
    @Qualifier("dashScopeChatModel")
    private ChatModel dashScopeChatModel;

    @Autowired
    private ConversationHistoryMessagesStorage conversationHistoryMessagesStorage;
    @Autowired
    private ConversationMapper cconversationMapper;
    @Autowired
    private ConversationMemoryMapper conversationMemoryMapper;

    @Override
    public Flux<String> chat(ChatDTO chatDTO) throws GraphStateException {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        String input = chatDTO.getMessage();
        Integer currentRound = chatDTO.getCurrentRound();
        String threadId = chatDTO.getConversationId() != null ? String.valueOf(chatDTO.getConversationId()) : BaseCheckpointSaver.THREAD_ID_DEFAULT;

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata(Conversation.CURRENT_ROUND, currentRound)
                .addMetadata(GraphConstant.USER_ID, currentId)
                .addMetadata(GraphConstant.USER_INPUT, input)
                .build();

        // 定义状态策略
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(OverAllState.DEFAULT_INPUT_KEY, new ReplaceStrategy());
            keyStrategyMap.put(GraphConstant.MESSAGES, new AppendStrategy());
            keyStrategyMap.put(GraphConstant.STREAM_RESULT, new AppendStrategy());
            keyStrategyMap.put(GraphConstant.CONVERSATION_MESSAGES, new AppendStrategy());
            return keyStrategyMap;
        };

        // 节点设置
        EmotionRecognitionNode emotionRecognitionNode = EmotionRecognitionNode.builder().chatModel(deepSeekChatModel).build();
        EmotionalDiagnosisNode emotionalDiagnosisNode = EmotionalDiagnosisNode.builder().chatModel(dashScopeChatModel).build();
        FinalAnswerNode finalAnswerNode = FinalAnswerNode.builder().chatModel(deepSeekChatModel).build();

        // 创建图并设置对应节点与关系
        StateGraph workflow = new StateGraph(keyStrategyFactory)
                .addNode(EmotionRecognitionNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(emotionRecognitionNode))
                .addNode(EmotionalDiagnosisNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(emotionalDiagnosisNode))
                .addNode(FinalAnswerNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(finalAnswerNode));

        workflow.addEdge(StateGraph.START, EmotionRecognitionNode.NODE_NAME);
        workflow.addEdge(EmotionRecognitionNode.NODE_NAME, EmotionalDiagnosisNode.NODE_NAME);
        workflow.addEdge(EmotionalDiagnosisNode.NODE_NAME, FinalAnswerNode.NODE_NAME);
        workflow.addEdge(FinalAnswerNode.NODE_NAME, StateGraph.END);

        var compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder()
                        .register(CustomMysqlSaver.builder().build())
                        .build())
                .build();

        // 创建编译后的图
        CompiledGraph graph = workflow.compile(compileConfig);

        // 设置会话状态
        Map<String, Object> stateMap = Map.of(OverAllState.DEFAULT_INPUT_KEY, input,
                GraphConstant.MESSAGES, UserMessage.builder().text(input).build(),
                GraphConstant.USER_ID, currentId);
        Flux<NodeOutput> stream = graph.stream(stateMap, runnableConfig);
        return streamChat(stream, runnableConfig, currentRound);

//        StringBuilder stringBuilder = new StringBuilder();
//        stream.subscribe(
//                output -> {
////                    log.debug("调用者输出:{}", output);
//                    // 检查是否为 StreamingOutput 类型
//                    if (output instanceof StreamingOutput streamingOutput) {
//                        OutputType type = streamingOutput.getOutputType();
//                        Message message = streamingOutput.message();
//
//                        // 处理模型流式输出
//                        if (type == OutputType.AGENT_MODEL_STREAMING) {
//                            if (message instanceof AssistantMessage assistantMessage) {
//                                // 检查是否为 Thinking 消息
//                                Object reasoningContent = assistantMessage.getMetadata().get("reasoningContent");
//                                if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
//                                    stringBuilder.append(reasoningContent);
//                                    log.debug("[Thinking] {}", reasoningContent);
//                                } else {
//                                    // 普通模型响应（增量内容）
//                                    log.debug("[Model] ============>>> {}", assistantMessage.getText());
//                                }
//                            }
//                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
//                            // 处理模型输出完成
//                            if (message instanceof AssistantMessage assistantMessage) {
//                                if (assistantMessage.hasToolCalls()) {
//                                    ConversationHistoryMessagesStorage.storeDataToConfig(runnableConfig, message);
//                                    // 工具调用请求
//                                    assistantMessage.getToolCalls().forEach(toolCall -> {
//                                        log.debug("[Tool Call] {} : {}", toolCall.name(), toolCall.arguments());
//                                    });
//                                } else {
//                                    // 到这里说明流式输出已经结束，这里的Output参数中的message属性中，存储的是模型流式输出的完整内容
//                                    log.debug("图流程执行完成");
//                                    // 处理流式输出结果，DB存储
//                                    Optional<String> threadIdOpl = runnableConfig.threadId();
//                                    Long conversationId = Long.valueOf(threadIdOpl.orElseThrow());
//
//                                    if(!stringBuilder.isEmpty()){
//                                        log.debug("最终模型思考流式输出结果：{}", stringBuilder);
//                                        Message thinkMessage = new AssistantMessage(stringBuilder.toString());
//                                        conversationHistoryMessagesStorage.save(conversationId, currentRound, List.of(thinkMessage, message));
//                                    } else {
//                                        conversationHistoryMessagesStorage.save(conversationId, currentRound, List.of(message));
//                                    }
//
//                                    log.info("最终模型流式输出结果：{}", message);
//                                    log.info("流式输出结果完整结果：{}", message.getText());
//
//                                    // 插入结果到后续的检查点中
//                                    output.state().input(Map.of(GraphConstant.MESSAGES, message));
//                                }
//                            }
//                        } else if (type == OutputType.AGENT_TOOL_FINISHED) {
//                            // 处理工具执行结果
//                            if (message instanceof ToolResponseMessage toolResponse) {
//                                toolResponse.getResponses().forEach(response -> {
//                                    log.debug("[Tool Result] {} : {}", response.name(), response.responseData());
//                                });
//                            }
//                        } else if (type == OutputType.AGENT_HOOK_FINISHED) {
//                            // 对于 Hook 节点，通常只关注完成事件（如果Hook没有有效输出可以忽略）
//                            log.debug("Hook 执行完成: {}", output.node());
//                        }
//                    } else {
//                        // 到这里说明是普通的NodeOutput类型
//                    }
//                },
//                error -> log.error("错误: {}", error),
//                () -> {
//                    log.info("Agent 执行完成");
//                }
//        );

    }

    private Flux<String> streamChat(Flux<NodeOutput> stream, RunnableConfig runnableConfig, int currentRound) {

        StringBuilder stringBuilder = new StringBuilder();

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        stream.subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                output -> {
                    if (output instanceof StreamingOutput streamingOutput) {
                        OutputType type = streamingOutput.getOutputType();
                        Message message = streamingOutput.message();

                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            if (message instanceof AssistantMessage assistantMessage) {
                                Object reasoningContent = assistantMessage.getMetadata().get("reasoningContent");
                                if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
                                    stringBuilder.append(reasoningContent);
                                    log.debug("[Thinking] {}", reasoningContent);
                                    sink.tryEmitNext((String) reasoningContent);
                                } else {
                                    String content = assistantMessage.getText();
                                    log.debug("[Model] ============>>> {}", content);
                                    sink.tryEmitNext(content);
                                }
                            }
                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                            if (message instanceof AssistantMessage assistantMessage) {
                                if (!assistantMessage.hasToolCalls()) {
                                    Optional<String> threadIdOpl = runnableConfig.threadId();
                                    Long conversationId = Long.valueOf(threadIdOpl.orElseThrow());

                                    if (!stringBuilder.isEmpty()) {
                                        log.debug("最终模型思考流式输出结果：{}", stringBuilder);
                                        Message thinkMessage = new AssistantMessage(stringBuilder.toString());
                                        conversationHistoryMessagesStorage.save(conversationId, currentRound, List.of(thinkMessage, message));
                                    } else {
                                        conversationHistoryMessagesStorage.save(conversationId, currentRound, List.of(message));
                                    }

                                    log.info("最终模型流式输出结果：{}", message);
                                    log.info("流式输出结果完整结果：{}", message.getText());
                                    output.state().input(Map.of(GraphConstant.MESSAGES, message));
                                }
                            }
                        }
                    }
                },
                error -> {
                    log.error("错误：{}", error);
                    sink.tryEmitError(error);
                },
                () -> {
                    log.info("Agent 执行完成");
                    boolean isFirstConversation = (boolean) runnableConfig.context().get(GraphConstant.CONVERSATION_FIRST);
                    if(isFirstConversation){
                        // todo 创建对应的会话名称（模型调用）
                        Optional<String> threadIdOpl = runnableConfig.threadId();
                        Long conversationId = Long.valueOf(threadIdOpl.orElseThrow());
                        // 查询会话数据
                        List<ConversationMemory> conversationMemories = conversationMemoryMapper.selectList(new LambdaQueryWrapper<ConversationMemory>()
                                .eq(ConversationMemory::getId, conversationId)
                                .eq(ConversationMemory::getRoundNum, 1));

                        List<String> list = conversationMemories.stream().map(item -> item.getContent()).toList();

                        String conversationName = CommonServerAgent.conversationNameExtraction(list);
                        cconversationMapper.updateById(Conversation.builder()
                                .id(conversationId)
                                .name(conversationName)
                                .build());
                        sink.tryEmitComplete();
                    }

                }
        );

        return sink.asFlux();
    }
}
