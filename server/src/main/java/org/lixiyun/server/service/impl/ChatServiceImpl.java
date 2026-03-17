package org.lixiyun.server.service.impl;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.dto.chat.ChatDTO;
import org.lixiyun.pojo.entity.Conversation;
import org.lixiyun.server.Saver.CustomMysqlSaver;
import org.lixiyun.server.constant.StreamConstant;
import org.lixiyun.server.node.EmotionRecognitionNode;
import org.lixiyun.server.node.EmotionalDiagnosisNode;
import org.lixiyun.server.node.FinalAnswerNode;
import org.lixiyun.server.service.ChatService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async;

/**
 * @author lixiyun
 * @since 2026-03-16 13:02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final EmotionalDiagnosisNode emotionalDiagnosisNode;
    private final EmotionRecognitionNode emotionRecognitionNode;
    private final FinalAnswerNode finalAnswerNode;
    private final CustomMysqlSaver customMysqlSaver;

    @Override
    public void chat(ChatDTO chatDTO) throws GraphStateException {
        String threadId = BaseCheckpointSaver.THREAD_ID_DEFAULT;
        if (chatDTO.getConversationId() != null) {
            threadId = String.valueOf(chatDTO.getConversationId());
        }

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata(Conversation.CURRENT_ROUND, chatDTO.getCurrentRound())
//                .addParallelNodeExecutor(emotionalDiagnosisNode.getNodeName(), ForkJoinPool.commonPool())
                .build();

        // 定义状态策略
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(OverAllState.DEFAULT_INPUT_KEY, new ReplaceStrategy());
            keyStrategyMap.put("messages", new AppendStrategy());
            keyStrategyMap.put(StreamConstant.STREAM_RESULT, new AppendStrategy());
            return keyStrategyMap;
        };

        StateGraph workflow = new StateGraph(keyStrategyFactory)
                .addNode(emotionRecognitionNode.getNodeName(), node_async(emotionRecognitionNode))
                .addNode(emotionalDiagnosisNode.getNodeName(), node_async(emotionalDiagnosisNode))
                .addNode(finalAnswerNode.getNodeName(), node_async(finalAnswerNode));

        workflow.addEdge(StateGraph.START, emotionRecognitionNode.getNodeName());
//        workflow.addEdge(emotionRecognitionNode.getNodeName(), finalAnswerNode.getNodeName());
        workflow.addEdge(emotionRecognitionNode.getNodeName(), emotionalDiagnosisNode.getNodeName());
        workflow.addEdge(emotionalDiagnosisNode.getNodeName(), finalAnswerNode.getNodeName());
        workflow.addEdge(finalAnswerNode.getNodeName(), StateGraph.END);

        var compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder()
                        .register(customMysqlSaver)
                        .build())
                .build();

        CompiledGraph graph = workflow.compile(compileConfig);

        Flux<NodeOutput> stream = graph.stream(Map.of(OverAllState.DEFAULT_INPUT_KEY, chatDTO.getMessage()), runnableConfig);
        stream.subscribe(
                output -> {
                    // 检查是否为 StreamingOutput 类型
                    if (output instanceof StreamingOutput streamingOutput) {
                        OutputType type = streamingOutput.getOutputType();
                        Message message = streamingOutput.message();

                        // 处理模型流式输出
                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            if (message instanceof AssistantMessage assistantMessage) {
                                // 检查是否为 Thinking 消息
                                Object reasoningContent = assistantMessage.getMetadata().get("reasoningContent");
                                if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
                                    System.out.print("[Thinking] " + reasoningContent);
                                } else {
                                    // 普通模型响应（增量内容）
                                    System.out.print(assistantMessage.getText());
                                }
                            }
                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                            // 处理模型输出完成
                            if (message instanceof AssistantMessage assistantMessage) {
                                if (assistantMessage.hasToolCalls()) {
                                    // 工具调用请求
                                    assistantMessage.getToolCalls().forEach(toolCall -> {
                                        System.out.println("[Tool Call] " + toolCall.name() + ": " + toolCall.arguments());
                                    });
                                } else {
                                    // 模型完整响应
                                    System.out.println("\n[Model Finished]");
                                }
                            }
                        } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                            // 处理工具执行结果
                            if (message instanceof ToolResponseMessage toolResponse) {
                                toolResponse.getResponses().forEach(response -> {
                                    System.out.println("[Tool Result] " + response.name() + ": " + response.responseData());
                                });
                            }
                        }

                        // 处理模型推理的流式输出
                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            // 流式增量内容，逐步显示
                            System.out.print(streamingOutput.message().getText());
                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                            // 模型推理完成，可获取完整响应
                            System.out.println("\n模型输出完成");
                        }

                        // 处理工具调用完成（目前不支持 STREAMING）
                        if (type == OutputType.AGENT_TOOL_FINISHED) {
                            System.out.println("工具调用完成: " + output.node());
                        }

                        // 对于 Hook 节点，通常只关注完成事件（如果Hook没有有效输出可以忽略）
                        if (type == OutputType.AGENT_HOOK_FINISHED) {
                            System.out.println("Hook 执行完成: " + output.node());
                        }
                    }
                },
                error -> System.err.println("错误: " + error),
                () -> System.out.println("Agent 执行完成")
        );

    }
}
