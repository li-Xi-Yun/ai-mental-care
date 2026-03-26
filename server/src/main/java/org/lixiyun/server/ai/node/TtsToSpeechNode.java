package org.lixiyun.server.ai.node;

import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioSpeechApi;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.websocket.entity.WebSocketMsg;
import org.lixiyun.common.websocket.enums.MessageType;
import org.lixiyun.common.websocket.util.WebSocketUtils;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.socket.cache.TtsStreamHolder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;

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

    @Value("${spring.ai.dashscope.api-key}")
    private final String apiKey;

    private final ChatModel chatModel;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("语音输出节点：开始执行");
        String audioDataResult = (String) config.context().get(GraphConstant.AUDIO_DATA_RESULT);
        if(audioDataResult == null){
            log.error("ttsToSpeechNode: 文本数据不存在");
            throw new BusinessException(ConversationExceptionEnum.AUDIO_TEXT_DATA_NOT_EXIST);
        }

        // DashScopeAudioSpeechOptions 配置参数
        DashScopeAudioSpeechOptions speechOptions = DashScopeAudioSpeechOptions.builder()
                .model("cosyvoice-v3-flash")    // 实时TTS模型
                .voice("longanhuan")           // 用于合成的语音。温柔女声 适用于CosyVoice系列的模型
                .instruction("你正在进行闲聊对话，你说话的情感是neutral。")  // 用于合成的语音音色 适用于CosyVoice系列的指定模型
                .sampleRate(48000)               // 合成音频的采样率
                .responseFormat(DashScopeAudioSpeechApi.ResponseFormat.WAV) // 音频输出的格式 wav
                .speed(1.0)                      // 语音合成的速度。可接受的范围是从 0.5 到 2.0
                .volume(70)                      // 合成音频的音量。范围：0-100
                .pitch(1.0)                      // 合成音频的音调。范围：0.5-2.0
                .build();

        var dashScopeAudioSpeechApi = new DashScopeAudioSpeechApi(apiKey);
        var dashScopeAudioSpeechModel = new DashScopeAudioSpeechModel(dashScopeAudioSpeechApi);

        Flux<byte[]> audioByteStream = dashScopeAudioSpeechModel.stream(audioDataResult, speechOptions);

        Optional<Object> userIdOpl = config.metadata(GraphConstant.USER_ID);
        Long userId = (Long) userIdOpl.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));

        long msgId = IdUtil.getSnowflakeNextId();
        // 流式发送音频数据到 WebSocket
        Disposable subscribe = audioByteStream
                .publishOn(Schedulers.boundedElastic())
                .subscribe(audioChunk -> {
                            try {
                                // 创建音频消息
                                WebSocketMsg<byte[]> audioMsg = WebSocketMsg.<byte[]>builder()
                                        .msgId(msgId)
                                        .msgType(MessageType.AUDIO_STREAM_RESULT.getName())
                                        .binaryData(audioChunk)
                                        .build();

                                // 通过 WebSocket 发送
                                WebSocketUtils.sendAudioMessage(userId, audioMsg);
                                log.debug("[TTS] 发送音频块，大小：{} bytes", audioChunk.length);
                            } catch (Exception e) {
                                log.error("[TTS] 发送音频块失败", e);
                            }
                        }, error -> {
                            log.error("[TTS] 音频流处理异常", error);

                            // 清理中断对象
                            TtsStreamHolder.clear(userId);
                        }, () -> {
                            log.info("[TTS] 音频流式输出完成");
                            // 发送结束标志
                            WebSocketMsg<Object> endMsg = WebSocketMsg.builder()
                                    .msgId(IdUtil.getSnowflakeNextId())
                                    .msgType(MessageType.AUDIO_STREAM_FINISH.getName())
                                    .build();
                            WebSocketUtils.sendMessage(userId, endMsg);

                            // 清理中断对象
                            TtsStreamHolder.clear(userId);
                        });

        // 保存订阅，用于后续中断处理
        TtsStreamHolder.put(userId, subscribe);

        return Map.of();
    }
}
