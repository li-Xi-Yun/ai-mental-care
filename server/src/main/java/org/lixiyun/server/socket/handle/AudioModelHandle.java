package org.lixiyun.server.socket.handle;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.socket.WebSocketMessageHandle;
import org.lixiyun.common.websocket.cache.AudioCache;
import org.lixiyun.server.ai.node.*;
import org.lixiyun.server.ai.saver.CustomMysqlSaver;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.socket.dto.AudioModelDTO;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

/**
 * @author lixiyun
 * @since 2026-03-23 20:38
 */
@Slf4j
@Component("audioModel")
public class AudioModelHandle implements WebSocketMessageHandle {

    @Autowired
    @Qualifier("deepSeekChatModel")
    private ChatModel deepSeekChatModel;

    @Autowired
    @Qualifier("dashScopeChatModel")
    private ChatModel dashScopeChatModel;

    @Autowired
    @Qualifier("ollamaChatModel")
    private ChatModel ollamaChatModel;

    @Override
    public void handle(Long userId, Object data) {
        log.info("音频模型调用，userId：{}", userId);
        byte[] audioData = AudioCache.getAndClearAudioData(userId);
        if(audioData.length == 0){
            log.warn("用户{}音频数据为空", userId);
            return;
        }

        if(!(data instanceof AudioModelDTO audioModelDTO)){
            log.warn("用户{}音频模型数据格式错误", userId);
            return;
        }

        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        Integer currentRound = audioModelDTO.getCurrentRound();
        String threadId = audioModelDTO.getConversationId() != null ? String.valueOf(audioModelDTO.getConversationId()) : BaseCheckpointSaver.THREAD_ID_DEFAULT;

        ArrayList<Message> messageList = new ArrayList<>();
        ArrayList<Message> streamResult = new ArrayList<>();
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata(GraphConstant.CURRENT_ROUND, currentRound)
                .addMetadata(GraphConstant.USER_ID, currentId)
                .addMetadata(GraphConstant.CONVERSATION_MESSAGES, messageList)
                .addMetadata(GraphConstant.STREAM_RESULT, streamResult)
                .addMetadata(GraphConstant.FINAL_ANSWER_STREAM, 0)
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
        AsrToTextNode asrToTextNode = AsrToTextNode.builder().chatModel(deepSeekChatModel).build(); // 语音转文字
        EmotionRecognitionNode emotionRecognitionNode = EmotionRecognitionNode.builder().chatModel(deepSeekChatModel).build(); // 文本情感识别(格式化存储)
        EmotionalDiagnosisNode emotionalDiagnosisNode = EmotionalDiagnosisNode.builder().chatModel(dashScopeChatModel).build(); // 情感诊断
        FinalAnswerNode finalAnswerNode = FinalAnswerNode.builder().chatModel(dashScopeChatModel).build(); // 最终回答
        TtsToSpeechNode ttsToSpeechNode = TtsToSpeechNode.builder().chatModel(ollamaChatModel).build(); // 文本转语音

        try {
            // 创建图并设置对应节点与关系
            StateGraph workflow = new StateGraph(keyStrategyFactory)
                    .addNode(AsrToTextNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(asrToTextNode))
                    .addNode(EmotionRecognitionNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(emotionRecognitionNode))
                    .addNode(EmotionalDiagnosisNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(emotionalDiagnosisNode))
                    .addNode(FinalAnswerNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(finalAnswerNode))
                    .addNode(TtsToSpeechNode.NODE_NAME, AsyncNodeActionWithConfig.node_async(ttsToSpeechNode));

            workflow.addEdge(StateGraph.START, AsrToTextNode.NODE_NAME);
            workflow.addEdge(AsrToTextNode.NODE_NAME, EmotionRecognitionNode.NODE_NAME);
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
            Map<String, Object> stateMap = Map.of(OverAllState.DEFAULT_INPUT_KEY, audioData);
            Flux<NodeOutput> stream = graph.stream(stateMap, runnableConfig);
        } catch (GraphStateException e) {
            throw new RuntimeException(e);
        }

    }

}
