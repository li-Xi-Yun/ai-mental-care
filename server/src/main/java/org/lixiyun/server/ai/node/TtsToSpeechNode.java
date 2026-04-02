package org.lixiyun.server.ai.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.infrastructure.audio.TtsStreamHolder;
import org.springframework.ai.chat.messages.Message;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * 语音输出节点
 *
 * @author lixiyun
 * @since 2026-03-24 20:57
 */
@Slf4j
@Builder
public class TtsToSpeechNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "ttsToSpeechNode";

//    @Value("${spring.ai.dashscope.api-key}")
    private final String apiKey;
    // 模型
    private final String model = "cosyvoice-v3-flash";
    // 音色
    private final String voice = "longanyang";

//    private final ChatModel chatModel;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("语音输出节点：开始执行");
        String audioDataResult = (String) config.context().get(GraphConstant.AUDIO_DATA_RESULT);
        Optional<List<Message>> userMessageOpl = state.value(GraphConstant.MESSAGES);
        List<Message> userMessages = userMessageOpl.orElse(null);

        if(userMessages == null){
            audioDataResult = "我没有听清你的讲什么，能再讲一次吗";
        } else if(audioDataResult == null){
            log.error("ttsToSpeechNode: 文本数据不存在");
            throw new BusinessException(ConversationExceptionEnum.AUDIO_TEXT_DATA_NOT_EXIST);
        }

        Optional<Object> userIdOpl = config.metadata(GraphConstant.USER_ID);
        Long userId = (Long) userIdOpl.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));

        Optional<Object> audioFluxOpl = config.metadata(GraphConstant.AUDIO_FLUX);
//        Sinks.Many<byte[]> audioSink = (Sinks.Many<byte[]>) audioFluxOpl.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));
        OutputStream outputStream = (OutputStream) audioFluxOpl.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));

        CountDownLatch latch = new CountDownLatch(1);

        // 实现回调接口ResultCallback
        ResultCallback<SpeechSynthesisResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                if (result.getAudioFrame() != null) {
                    try {
                        // 此处实现保存音频数据到本地的逻辑
                        byte[] audioData = result.getAudioFrame().array();
                        log.debug("[TTS] 发送音频块，大小：{} bytes", audioData.length);
                        // 发送音频消息
                        outputStream.write(audioData); // 直接写入前端流式输出
                        outputStream.flush(); // 强制刷新，实时传输
                    } catch (Exception e) {
                        log.error("写入流失败", e);
                    }
//                    audioSink.tryEmitNext(audioData);
                }
            }

            @Override
            public void onComplete() {
                log.info("[TTS] 音频流式输出完成");
                // 清理中断对象
                TtsStreamHolder.clear(userId);
                // 发送完成消息
//                audioSink.tryEmitComplete();
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                log.error("[TTS] 音频流处理异常", e);
                // 清理中断对象
                TtsStreamHolder.clear(userId);

                // 发送错误消息
//                audioSink.tryEmitError(e);
                latch.countDown();
            }
        };

        // 请求参数
        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                        .apiKey(apiKey)
                        .model(model) // 模型
                        .voice(voice) // 音色
                        .build();
        // 第二个参数“callback”传入回调即启用异步模式
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, callback);
        // 非阻塞调用，立即返回null（实际结果通过回调接口异步传递），在回调接口的onEvent方法中实时获取二进制音频
        try {
            synthesizer.call(audioDataResult);
            log.debug("[TTS] 模型执行成功");
            // 等待合成完成
            latch.await();
            // 等待播放线程全部播放完
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 任务结束后关闭websocket连接
            synthesizer.getDuplexApi().close(1000, "bye");

            // 关闭流式通道
            outputStream.close();
            log.debug("[TTS] 输出流已关闭");
        }

        // 保存订阅，用于后续中断处理
        TtsStreamHolder.put(userId, synthesizer);


        return Map.of();
    }










//        // DashScopeAudioSpeechOptions 配置参数
//        DashScopeAudioSpeechOptions speechOptions = DashScopeAudioSpeechOptions.builder()
//                .model("cosyvoice-v3-flash")    // 实时TTS模型
//                .voice("longanhuan")           // 用于合成的语音。温柔女声 适用于CosyVoice系列的模型
//                .instruction("你正在进行闲聊对话，你说话的情感是neutral。")  // 用于合成的语音音色 适用于CosyVoice系列的指定模型
//                .sampleRate(48000)               // 合成音频的采样率
//                .responseFormat(DashScopeAudioSpeechApi.ResponseFormat.WAV) // 音频输出的格式 wav
//                .speed(1.0)                      // 语音合成的速度。可接受的范围是从 0.5 到 2.0
//                .volume(70)                      // 合成音频的音量。范围：0-100
//                .pitch(1.0)                      // 合成音频的音调。范围：0.5-2.0
//                .build();
//
//        var dashScopeAudioSpeechApi = new DashScopeAudioSpeechApi(apiKey);
//        var dashScopeAudioSpeechModel = new DashScopeAudioSpeechModel(dashScopeAudioSpeechApi);
//        Flux<byte[]> audioByteStream = dashScopeAudioSpeechModel.stream(audioDataResult, speechOptions);
//        // 流式发送音频数据到 WebSocket
//        Disposable subscribe = audioByteStream
//                .publishOn(Schedulers.boundedElastic())
//                .subscribe(audioChunk -> {
//                            log.debug("[TTS] 发送音频块，大小：{} bytes", audioChunk.length);
//                            // 发送音频消息
//                            audioSink.tryEmitNext(audioChunk);
//                        }, error -> {
//                            log.error("[TTS] 音频流处理异常", error);
//                            // 清理中断对象
//                            TtsStreamHolder.clear(userId);
//
//                            // 发送错误消息
//                            audioSink.tryEmitError(error);
//
//                        }, () -> {
//                            log.info("[TTS] 音频流式输出完成");
//                            // 清理中断对象
//                            TtsStreamHolder.clear(userId);
//                            // 发送完成消息
//                            audioSink.tryEmitComplete();
//                        });
//
//        // 保存订阅，用于后续中断处理
//        TtsStreamHolder.put(userId, subscribe);
//
//        return Map.of();
}
