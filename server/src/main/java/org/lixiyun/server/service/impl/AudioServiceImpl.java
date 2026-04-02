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
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.conversation.AudioModelDTO;
import org.lixiyun.pojo.entity.Conversation;
import org.lixiyun.server.ai.node.*;
import org.lixiyun.server.ai.saver.CustomMysqlSaver;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.infrastructure.audio.AudioCache;
import org.lixiyun.server.infrastructure.storage.FileStorage;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.service.AudioService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

/**
 * @author lixiyun
 * @since 2026-03-29 18:17
 */
@Slf4j
@Service
public class AudioServiceImpl implements AudioService {

//    @Value("${spring.ai.dashscope.api-key}")
//    private String apiKey;
//
//    @Autowired
//    @Qualifier("deepSeekChatModel")
//    private ChatModel deepSeekChatModel;
//
//    @Autowired
//    @Qualifier("dashScopeChatModel")
//    private ChatModel dashScopeChatModel;
//
//    @Autowired
//    @Qualifier("ollamaChatModel")
//    private ChatModel ollamaChatModel;

    private final String apiKey;
    private final ChatModel deepSeekChatModel;
    private final ChatModel dashScopeChatModel;
    private final ChatModel ollamaChatModel;
    private final FileStorage fileStorage;
    private final ConversationMapper conversationMapper;

    public AudioServiceImpl(
            @Value("${spring.ai.dashscope.api-key}") String apiKey,
            @Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel,
            @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            FileStorage fileStorage,
            ConversationMapper conversationMapper
    ) {
        // 使用构造器注入，防止异步线程执行时，@Value 未注入，因为该字段是“晚加载”的配置，其他三个是项目一启动，就会创建的，永远存在内存里，永不为null
        this.apiKey = apiKey;
        this.deepSeekChatModel = deepSeekChatModel;
        this.dashScopeChatModel = dashScopeChatModel;
        this.ollamaChatModel = ollamaChatModel;
        this.fileStorage = fileStorage;
        this.conversationMapper = conversationMapper;
    }

    @Override
    public void audioModel(AudioModelDTO audioModelDTO, OutputStream outputStream) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        // 获取并清空缓存的音频数据
        byte[] audioData = AudioCache.getAndClearAudioData(currentId);
        if (audioData.length == 0) {
            log.error("语音对话-用户{}音频数据为空", currentId);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_DATA_NOT_EXIST);
        }
        String audioUrl = fileStorage.localBinaryStorage(audioData, "mp3", FileStorage.StorageType.AUDIO);
        log.info("语音对话-用户{}上传音频成功，音频地址为：{}", currentId, audioUrl);
        ByteBuffer audioDataBuffer = ByteBuffer.wrap(audioData);

        Integer currentRound = audioModelDTO.getCurrentRound();
        String threadId = audioModelDTO.getConversationId() != null
                ? String.valueOf(audioModelDTO.getConversationId())
                : BaseCheckpointSaver.THREAD_ID_DEFAULT;

//        Sinks.Many<byte[]> sink = Sinks.many().unicast().onBackpressureBuffer();  // 流式返回值
        ArrayList<Message> messageList = new ArrayList<>();
        ArrayList<Message> streamResult = new ArrayList<>();
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata(GraphConstant.CURRENT_ROUND, currentRound)
                .addMetadata(GraphConstant.USER_ID, currentId)
                .addMetadata(GraphConstant.CONVERSATION_MESSAGES, messageList)
                .addMetadata(GraphConstant.STREAM_RESULT, streamResult)
                .addMetadata(GraphConstant.FINAL_ANSWER_STREAM, 0)
                .addMetadata(GraphConstant.AUDIO_DATA, audioDataBuffer)
                .addMetadata(GraphConstant.AUDIO_FLUX, outputStream)
                // 添加并行节点
                .addParallelNodeExecutor(EmotionalDiagnosisNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(FinalAnswerNode.NODE_NAME, ForkJoinPool.commonPool())
                .build();

        // 定义状态策略
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put(GraphConstant.INPUT, new ReplaceStrategy());
            keyStrategyMap.put(GraphConstant.MESSAGES, new AppendStrategy());
            return keyStrategyMap;
        };

        // 节点设置
        AsrToTextNode asrToTextNode = AsrToTextNode.builder().apiKey(apiKey).build(); // 语音转文字
        EmotionRecognitionNode emotionRecognitionNode = EmotionRecognitionNode.builder().chatModel(deepSeekChatModel).build(); // 文本情感识别 (格式化存储)
        EmotionalDiagnosisNode emotionalDiagnosisNode = EmotionalDiagnosisNode.builder().chatModel(dashScopeChatModel).build(); // 情感诊断
        FinalAnswerNode finalAnswerNode = FinalAnswerNode.builder().chatModel(dashScopeChatModel).build(); // 最终回答
        TtsToSpeechNode ttsToSpeechNode = TtsToSpeechNode.builder().apiKey(apiKey).build(); // 文本转语音

        try {
            // 创建图并设置对应节点与关系
            StateGraph workflow = new StateGraph(keyStrategyFactory)
                    .addNode(AsrToTextNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(asrToTextNode))
                    .addNode(EmotionRecognitionNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(emotionRecognitionNode))
                    .addNode(EmotionalDiagnosisNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(emotionalDiagnosisNode))
                    .addNode(FinalAnswerNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(finalAnswerNode))
                    .addNode(TtsToSpeechNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(ttsToSpeechNode));

            workflow.addEdge(StateGraph.START, AsrToTextNode.NODE_NAME);
            workflow.addConditionalEdges(
                    AsrToTextNode.NODE_NAME,
                    (state) -> {
                        // 从状态中获取 input
                        Optional<Object> inputOpl = state.value(GraphConstant.INPUT);
                        // 判断：input 不为空且不为空白字符串 → 走情感识别
                        if (inputOpl.isPresent() && !inputOpl.get().toString().isBlank()) {
                            return CompletableFuture.completedFuture("process"); // 正常处理
                        } else {
                            return CompletableFuture.completedFuture("skip"); // 直接跳过
                        }
                    },
                    Map.of(
                            "process", EmotionRecognitionNode.NODE_NAME,
                            "skip", TtsToSpeechNode.NODE_NAME
                    )
            );
            workflow.addEdge(EmotionRecognitionNode.NODE_NAME, EmotionalDiagnosisNode.NODE_NAME);
            workflow.addEdge(EmotionRecognitionNode.NODE_NAME, FinalAnswerNode.NODE_NAME);
            workflow.addEdge(EmotionalDiagnosisNode.NODE_NAME, TtsToSpeechNode.NODE_NAME);
            workflow.addEdge(FinalAnswerNode.NODE_NAME, TtsToSpeechNode.NODE_NAME);
            workflow.addEdge(TtsToSpeechNode.NODE_NAME, StateGraph.END);

            var compileConfig = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder()
                            .register(CustomMysqlSaver.builder().build())
                            .build())
                    .build();

            // 创建编译后的图
            CompiledGraph graph = workflow.compile(compileConfig);

            // 设置会话状态
            Map<String, Object> stateMap = Map.of();

            // 执行流式处理（结果通过 NodeOutput 返回，需要在节点内部通过 WebSocketUtils 发送）
            Flux<NodeOutput> stream = graph.stream(stateMap, runnableConfig);
//            Disposable subscribe = stream.subscribe();

            CountDownLatch latch = new CountDownLatch(1);
            // 执行图
            stream.subscribe(output -> {
//                                log.debug("语音对话-流式前置打印信息：{}", output);
                                if (output instanceof StreamingOutput streamingOutput) {
                                    OutputType type = streamingOutput.getOutputType();
                                    Message message = streamingOutput.message();

                                    if (type == OutputType.AGENT_MODEL_STREAMING) {
                                        if (message instanceof AssistantMessage assistantMessage) {
                                            Object reasoningContent = assistantMessage.getMetadata().get("reasoningContent");
                                            if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
                                                log.debug("语音对话-[模型思考输出] {}", reasoningContent);
                                            } else {
                                                String content = assistantMessage.getText();
                                                log.debug("语音对话-[模型流式输出结果] ============>>> {}", content);
                                            }
                                        }
                                    } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                        if (message instanceof AssistantMessage assistantMessage) {
                                            if(assistantMessage.hasToolCalls()){
                                                // 工具调用请求
                                                assistantMessage.getToolCalls().forEach(toolCall -> {
                                                    log.debug("语音对话-[Tool Call] {} : {}", toolCall.name(), toolCall.arguments());
                                                });
                                            } else {
                                                // 到这里说明流式输出已经结束，这里的Output参数中的message属性中，存储的是模型流式输出的完整内容
                                                log.debug("语音对话-模型流式输出结果：{}", message.getText());
                                                log.debug("语音对话-流式输出完整结果：{}", message);
                                            }
                                        }
                                    } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                        // 处理工具执行结果
                                        if (message instanceof ToolResponseMessage toolResponse) {
                                            toolResponse.getResponses().forEach(response -> {
                                                log.debug("语音对话-工具调用结果 {} : {}", response.name(), response.responseData());
                                            });
                                        }
                                    } else if (type == OutputType.AGENT_HOOK_FINISHED) {
                                        // 对于 Hook 节点，通常只关注完成事件（如果Hook没有有效输出可以忽略）
                                        log.debug("语音对话-Hook 执行完成: {}", output.node());
                                    }

                                } else {
                                    // 到这里说明是普通的NodeOutput类型
                                }
                            },
                            error -> {
                                log.error("语音对话-错误", error);
                            },
                            () -> {
                                log.info("语音对话-图执行完成");
                                if(currentRound == 1){
                                    // 使用WebSocket发送会话id，让前端识别该会话的会话id

                                } else {
                                    Optional<String> threadIdOpl = runnableConfig.threadId();
                                    Long conversationId = Long.valueOf(threadIdOpl.orElseThrow());
                                    conversationMapper.updateById(Conversation.builder()
                                            .id(conversationId)
                                            .currentRound(currentRound)
                                            .build());
                                }
                                latch.countDown();

                            }
            );

            // 等待图执行完成，防止异步线程执行，主线程提前返回，导致 outputStream 提前关闭
            latch.await();

        } catch (GraphStateException e) {
            log.error("语音对话-模型执行流式处理异常：{}", e.getMessage());
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


    }


}
