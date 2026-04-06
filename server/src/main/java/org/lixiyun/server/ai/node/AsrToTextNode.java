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

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * ASR 语音转文本并获取对应的语音情绪分析
 * @author lixiyun
 * @since 2026-03-24 20:54
 */
@Slf4j
@Builder
public class AsrToTextNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "asrToTextNode";

//    @Value("${spring.ai.dashscope.api-key}")
    private final String apiKey;

//    private final ChatModel chatModel;

    private final String IDENTIFY_TEXT = "identifyText";
    private final String SPEECH_EMOTION = "speechEmotion";

    private RecognitionParam recognitionParam(){
        return RecognitionParam.builder()
                 // 阿里的密匙
                .apiKey(apiKey)
                .model("paraformer-realtime-8k-v2")
                // 音频格式：wav
                .format("mp3")
                // 采样率（阿里云要求的标准采样率）
                .sampleRate(8000)
                // 语言提示：支持中文+英文（仅v2模型支持）
//                .parameter("language_hints", new String[]{"zh", "en"})
                .build();
    }

    private ResultCallback<RecognitionResult> initializeCallback(Map<String, String> map, CountDownLatch latch) {
        String threadName = Thread.currentThread().getName();
        return new ResultCallback<>() {
            @Override
            public void onEvent(RecognitionResult message) {
                log.debug("[ASR] 收到回调：{}", message);
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
                latch.countDown(); // 释放阻塞
            }

            @Override
            public void onError(Exception e) {
                log.error("[ASR] [process {}] 模型调用错误: {}", threadName, e.getMessage());
                latch.countDown();
            }
        };
    }

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("[ASR] 语音情绪识别节点：开始执行");
        Optional<Object> audioDataOpl = config.metadata(GraphConstant.AUDIO_DATA);
        ByteBuffer audioBuffer = (ByteBuffer) audioDataOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.AUDIO_DATA_NOT_EXIST));

        // 创建Recognition实例
        Recognition recognizer = new Recognition();
        RecognitionParam param = recognitionParam();
        Map<String, String> map = new HashMap<>();

        // 阻塞主线程，等待 ASR 回调完成
        CountDownLatch latch = new CountDownLatch(1);
        ResultCallback<RecognitionResult> callback = initializeCallback(map, latch);


        byte[] audioData = audioBuffer.array();
        try (ByteArrayInputStream bis = new ByteArrayInputStream(audioData)){
            recognizer.call(param, callback);
            byte[] buffer = new byte[3200];
            int bytesRead;
            // 循环读取音频二进制流，发送给识别模型
            while ((bytesRead = bis.read(buffer)) != -1) {
                ByteBuffer byteBuffer;
                if (bytesRead < buffer.length) {
                    byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
                } else {
                    byteBuffer = ByteBuffer.wrap(buffer);
                }

                recognizer.sendAudioFrame(byteBuffer);
                buffer = new byte[3200];

                Thread.sleep(100);
            }

            recognizer.stop();
        } catch (Exception e) {
            log.error("[ASR] [process {}] 语音情绪识别节点执行失败: {}", Thread.currentThread().getName(), e.getMessage());
            throw e;
        } finally {
            // 最终执行：无论是否报错，都关闭WebSocket长连接
            // 1000=正常关闭码，bye=关闭原因
            recognizer.getDuplexApi().close(1000, "bye");
        }

        String userEmotion = map.get(SPEECH_EMOTION);
        if (userEmotion != null) {
            // 存储语音语气情绪识别结果，用于后续情绪分析节点的额外输入
            config.context().put(GraphConstant.AUDIO_DATA_EMOTION_RECOGNITION, userEmotion);
        }

        String userInput = map.get(IDENTIFY_TEXT);
        UserMessage userInputMessage;
        if (userInput != null) {
            Optional<Object> conversationMessageOpl = config.metadata(GraphConstant.CONVERSATION_MESSAGES);
            List<Message> conversationMessage = (List<Message>) conversationMessageOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.CONVERSATION_METADATA_NOT_CONFIGURED));

            // 将识别结果存储，用于记录点插入时上下文信息保存
            userInputMessage = new UserMessage(userInput);
            conversationMessage.add(userInputMessage);

            log.debug("[ASR] 语音识别结果：{}", userInputMessage);
            return Map.of(GraphConstant.INPUT, userInput, GraphConstant.MESSAGES, userInputMessage);
        }

        log.info("[ASR] 语音识别节点-执行结束，用户输入：{}", userInput);

        // todo 暂时不做纯情绪分析的内容，例：有哭声，但没有文本语音输入
        return Map.of();
    }


}
