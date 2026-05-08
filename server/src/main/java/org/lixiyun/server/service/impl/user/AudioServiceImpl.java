package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
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
import org.lixiyun.common.agent.constant.prompt.ScenarioConstant;
import org.lixiyun.common.agent.prompt.utils.PromptUtil;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.websocket.utils.WebSocketUtils;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.user.conversation.AudioModelDTO;
import org.lixiyun.pojo.entity.conversation.Conversation;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.GraphCheckpoint;
import org.lixiyun.pojo.vo.user.conversation.ConversationVO;
import org.lixiyun.server.ai.infrastructure.agent.CommonServerAgent;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.ai.node.*;
import org.lixiyun.server.ai.rag.graph.RagGraph;
import org.lixiyun.server.ai.saver.CustomMysqlSaver;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.infrastructure.audio.AudioCache;
import org.lixiyun.server.mapper.ConversationMapper;
import org.lixiyun.server.mapper.ConversationMemoryMapper;
import org.lixiyun.server.mapper.GraphCheckpointMapper;
import org.lixiyun.server.service.user.AudioService;
import org.lixiyun.server.service.user.ConversationService;
import org.lixiyun.server.socket.constant.AudioConstant;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author lixiyun
 * @since 2026-03-29 18:17
 */
@Slf4j
@Service
public class AudioServiceImpl implements AudioService {

    private final String apiKey;
    private final ChatModel deepSeekChatModel;
    private final ChatModel dashScopeChatModel;
    private final ChatModel ollamaChatModel;
    private final ConversationMapper conversationMapper;
    private final ConversationMemoryMapper conversationMemoryMapper;
    private final GraphCheckpointMapper graphCheckpointMapper;
    private final ConversationService conversationService;
    private final RagGraph ragGraph;

    public AudioServiceImpl(
            @Value("${spring.ai.dashscope.api-key}") String apiKey,
            @Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel,
            @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
            @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            ConversationMapper conversationMapper,
            ConversationMemoryMapper conversationMemoryMapper,
            GraphCheckpointMapper graphCheckpointMapper,
            ConversationService conversationService, RagGraph ragGraph
    ) {
        // 使用构造器注入，防止异步线程执行时，@Value 未注入，因为该字段是“晚加载”的配置，其他三个是项目一启动，就会创建的，永远存在内存里，永不为null
        this.apiKey = apiKey;
        this.deepSeekChatModel = deepSeekChatModel;
        this.dashScopeChatModel = dashScopeChatModel;
        this.ollamaChatModel = ollamaChatModel;
//        this.fileStorage = fileStorage;
        this.conversationMapper = conversationMapper;
        this.conversationMemoryMapper = conversationMemoryMapper;
        this.graphCheckpointMapper = graphCheckpointMapper;
        this.conversationService = conversationService;
        this.ragGraph = ragGraph;
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
//        String audioUrl = fileStorage.localBinaryStorage(audioData, "mp3", FileStorage.StorageType.AUDIO);
//        log.info("语音对话-用户{}上传音频成功，音频地址为：{}", currentId, audioUrl);
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
                .addMetadata(GraphConstant.INVALID_CONVERSATION_INFO, new AtomicBoolean(false))
                // 添加并行节点
                .addParallelNodeExecutor(EmotionalDiagnosisNode.NODE_NAME, ForkJoinPool.commonPool())
                .addParallelNodeExecutor(FinalAnswerNode.NODE_NAME, ForkJoinPool.commonPool())
                .build();

        try {
            StateGraph workflow = getStateGraph();

            var compileConfig = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder()
                            .register(CustomMysqlSaver.builder().build())
                            .build())
                    .build();

            // 创建编译后的图
            CompiledGraph graph = workflow.compile(compileConfig);

            // 设置会话状态
            Map<String, Object> stateMap = Map.of();

            // 编译图
            Flux<NodeOutput> stream = graph.stream(stateMap, runnableConfig);

            // 执行图
            executeStream(stream, runnableConfig, threadId, currentRound, currentId);

        } catch (Exception e) {
            log.error("语音对话-模型执行流式处理异常：{}", e.getMessage());
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 获取状态图
     * @return 状态图
     * @throws GraphStateException 状态图异常
     */
    private StateGraph getStateGraph() throws GraphStateException {
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
        EmotionalDiagnosisNode emotionalDiagnosisNode = EmotionalDiagnosisNode.builder().chatModel(dashScopeChatModel).ragGraph(ragGraph).build(); // 情感诊断
        FinalAnswerNode finalAnswerNode = FinalAnswerNode.builder().chatModel(dashScopeChatModel)
                .modelPrompt(PromptUtil.getPrompt(ScenarioConstant.PROFESSIONAL_EMOTIONAL_COMPANION)).build(); // 最终回答
        TtsToSpeechNode ttsToSpeechNode = TtsToSpeechNode.builder().apiKey(apiKey).build(); // 文本转语音

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
        return workflow;
    }

    /**
     * 执行图
     *
     * @param stream 模型执行流
     * @param runnableConfig 运行时配置
     * @param threadId 会话id
     * @param currentRound 当前轮次
     * @param currentId 当前用户id
     * @throws InterruptedException 线程中断异常
     */
    private void executeStream(Flux<NodeOutput> stream,
                               RunnableConfig runnableConfig,
                               String threadId,
                               Integer currentRound,
                               Long currentId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        // 执行图
        stream.subscribe(output -> {
//            log.debug("语音对话-流式前置打印信息：{}", output);
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
            log.error("语音对话-图级别报错", error);
        },
        () -> {
            log.info("语音对话-图执行完成");
            Optional<String> threadIdOpl = runnableConfig.threadId();
            Long conversationId = Long.valueOf(threadIdOpl.orElseThrow());
            if(threadId.equals(BaseCheckpointSaver.THREAD_ID_DEFAULT) && currentRound == 1){
                // 使用WebSocket发送会话id，让前端识别该会话的会话id
                WebSocketUtils.sendToUserBySubDestination(String.valueOf(currentId), AudioConstant.AUDIO_CONVERSATION_ID, "\"" + conversationId.toString() + "\"");
            } else {
                conversationMapper.updateById(Conversation.builder()
                        .id(conversationId)
                        .currentRound(currentRound)
                        .build());
            }

            // 判断是否是无效语音输入，是，本次的检查点数据清除
            Optional<Object> invalidConversationInfoOpl = runnableConfig.metadata(GraphConstant.INVALID_CONVERSATION_INFO);
            AtomicBoolean invalidConversationInfo = (AtomicBoolean) invalidConversationInfoOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.CONVERSATION_METADATA_NOT_CONFIGURED));
            if (invalidConversationInfo.get()) {
                log.debug("语音对话-[无效语音输入] {}", invalidConversationInfo);
                graphCheckpointMapper.delete(new LambdaQueryWrapper<GraphCheckpoint>()
                        .eq(GraphCheckpoint::getConversationId, conversationId)
                        .eq(GraphCheckpoint::getRoundNum, currentRound)
                        .eq(GraphCheckpoint::getDeleted, DeleteConstant.DELETE_FLAG_NO));
            }

            latch.countDown();
        }
        );

        // 等待图执行完成，防止异步线程执行，主线程提前返回，导致 outputStream 提前关闭
        latch.await();
    }

    @Override
    public ConversationVO audioStop(Long conversationId) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        // 清空对话音频缓存
        AudioCache.clearAudioCache(currentId);

        if (conversationId != null) {
            // 进行语音对话关闭时，判断该对话是否存储有效会话信息，没有，进行会话删除
            Long count = conversationMemoryMapper.selectCount(new LambdaQueryWrapper<ConversationMemory>()
                    .eq(ConversationMemory::getConversationId, conversationId)
                    .eq(ConversationMemory::getUserId, currentId)
                    .eq(ConversationMemory::getDeleted, DeleteConstant.DELETE_FLAG_NO));
            if(count == 0){
                conversationService.deleteConversation(conversationId);
                return null;
            }
        }
        Conversation conversation = conversationMapper.selectById(conversationId);
        return BeanUtil.copyProperties(conversation, ConversationVO.class);
    }

    @Override
    public String initializeConversationName(Long conversationId) {
        List<ConversationMemory> conversationMemoryList = conversationMemoryMapper.selectList(new LambdaQueryWrapper<ConversationMemory>()
                .eq(ConversationMemory::getConversationId, conversationId)
                .eq(ConversationMemory::getUserId, UserInfoThreadLocalUtil.getCurrentIdThrow())
                .eq(ConversationMemory::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByAsc(ConversationMemory::getRoundNum)
                .last("limit 0, 20"));
        String historyInfo = conversationMemoryList.stream()
                .map(item -> {
                    String content = "第" +item.getRoundNum() + "轮-";
                    if(item.getType().equals(MessageType.USER)){
                        content += "用户输入：" + item.getContent();
                    } else if(item.getType().equals(MessageType.ASSISTANT)) {
                        content += "模型输出：" + item.getContent();
                    } else {
                        content += "模型思考：" + item.getContent();
                    }
                    return content;
                })
                .collect(Collectors.joining("\n"));
        // 创建对应的会话名称（模型调用）
        String conversationName = CommonServerAgent.builder().chatModel(dashScopeChatModel).build()
                .conversationNameExtraction(List.of(historyInfo));
        conversationMapper.updateById(Conversation.builder()
                .id(conversationId)
                .name(conversationName)
                .build());
        return conversationName;
    }


}
