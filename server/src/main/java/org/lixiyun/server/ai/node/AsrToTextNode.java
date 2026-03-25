package org.lixiyun.server.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.audio.asr.recognition.timestamp.Sentence;
import com.alibaba.dashscope.common.ResultCallback;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ASR 语音转文本并获取对应的语音情绪分析
 * @author lixiyun
 * @since 2026-03-24 20:54
 */
@Slf4j
@Builder
public class AsrToTextNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "asrToTextNode";

    @Value("${spring.ai.dashscope.api-key}")
    private final String apiKey;

    private final ChatModel chatModel;

    private final String IDENTIFY_TEXT = "identifyText";
    private final String SPEECH_EMOTION = "speechEmotion";

    private RecognitionParam recognitionParam(){
        return RecognitionParam.builder()
                // 若没有将API Key配置到环境变量中，需将apiKey替换为自己的API Key
                .apiKey(apiKey)
                .model("paraformer-realtime-8k-v2")
                // 音频格式：wav
                .format("wav")
                // 采样率：16000Hz（阿里云要求的标准采样率）
                .sampleRate(16000)
                // 语言提示：支持中文+英文（仅v2模型支持）
                .parameter("language_hints", new String[]{"zh", "en"})
                .build();
    }

    private ResultCallback<RecognitionResult> initializeCallback(Map<String, String> map) {
        String threadName = Thread.currentThread().getName();
        return new ResultCallback<>() {
            @Override
            public void onEvent(RecognitionResult message) {
                if (message.isSentenceEnd()) {
                    Sentence sentence = message.getSentence();
                    String emoTag = sentence.getEmoTag();
                    Double emoConfidence = sentence.getEmoConfidence();
                    String text = sentence.getText();
                    map.put(IDENTIFY_TEXT, text);
                    String temp = "情绪标签：" + emoTag + "，置信度：" + emoConfidence;
                    map.put(SPEECH_EMOTION, temp);
                    log.debug("[ASR] [process {}] 最终结果:{}", threadName, message);
                } else {
                    log.debug("[ASR] [process {}] 流式响应: {}", threadName, message);
                }
            }

            @Override
            public void onComplete() {
                log.debug("[ASR] [process {}] 模型调用完成", threadName);
            }

            @Override
            public void onError(Exception e) {
                log.error("[ASR] [process {}] 模型调用错误: {}", threadName, e.getMessage());
            }
        };
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("[ASR] 语音情绪识别节点：开始执行");
        Optional<Object> voiceDataOpl = state.value(GraphConstant.INPUT);
        byte[] voiceData = (byte[]) voiceDataOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.AUDIO_DATA_NOT_EXIST));

        // 创建Recognition实例
        Recognition recognizer = new Recognition();
        RecognitionParam param = recognitionParam();
        Map<String, String> map = new HashMap<>();
        ResultCallback<RecognitionResult> callback = initializeCallback(map);

        try {
            // 启动识别：建立WebSocket长连接，传入参数和回调
            recognizer.call(param, callback);

            // 核心：发送音频帧给阿里云识别服务
            ByteBuffer byteBuffer = ByteBuffer.wrap(voiceData);
            recognizer.sendAudioFrame(byteBuffer);

            // 停止识别：通知阿里云服务「音频发送完毕」
            recognizer.stop();
        } catch (Exception e) {
            log.error("[ASR] [process {}] 语音情绪识别节点执行失败: {}", Thread.currentThread().getName(), e.getMessage());
            throw e;
        } finally {
            // 最终执行：无论是否报错，都关闭WebSocket长连接
            // 1000=正常关闭码，bye=关闭原因
            recognizer.getDuplexApi().close(1000, "bye");
        }

        // 存储语音语气情绪识别结果，用于后续情绪分析节点的额外输入
        config.context().put(GraphConstant.AUDIO_DATA_EMOTION_RECOGNITION, map.get(SPEECH_EMOTION));

        // 将识别结果存储，用于记录点插入时上下文信息保存
        UserMessage userInput = new UserMessage(map.get(IDENTIFY_TEXT));
        Optional<Object> conversationMessageOpl = config.metadata(GraphConstant.CONVERSATION_MESSAGES);
        List<Message> conversationMessage = (List<Message>) conversationMessageOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.CONVERSATION_METADATA_NOT_CONFIGURED));
        conversationMessage.add(userInput);

        log.debug("[ASR] 语音识别结果：{}", userInput);
        return Map.of(GraphConstant.INPUT, userInput, GraphConstant.MESSAGES, userInput);
    }


}
