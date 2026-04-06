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
import org.lixiyun.common.websocket.utils.WebSocketUtils;
import org.lixiyun.server.constant.GraphConstant;
import org.lixiyun.server.infrastructure.audio.TtsStreamHolder;
import org.lixiyun.server.socket.constant.AudioConstant;
import org.springframework.ai.chat.messages.Message;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final String apiKey;
    // 模型
    private final String model = "cosyvoice-v3-flash";
    // 音色
    private final String voice = "longanyang";
    // 自动回复无效文本
    private final List<String> INVALID_REPLY_TEXT = List.of(
            "我没有听清你的讲什么，能再讲一次吗",
            "刚才听得不太清楚，你再说一遍可以吗",
            "抱歉，刚才没听清，能再讲一遍可以吗",
            "我这边听得不太清楚，能再讲一遍刚才的内容吗",
            "我这边没听清，能重新说一下吗");

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("[TTS] 开始执行");
        String audioTextData = (String) config.context().get(GraphConstant.AUDIO_TEXT_DATA);
        Optional<List<Message>> userMessageOpl = state.value(GraphConstant.MESSAGES);
        List<Message> userMessages = userMessageOpl.orElse(null);

        Optional<Object> userIdOpl = config.metadata(GraphConstant.USER_ID);
        Long userId = (Long) userIdOpl.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));
        if(userMessages == null){
            Random random = new Random();
            // 本轮数据无效
            audioTextData = INVALID_REPLY_TEXT.get(random.nextInt(INVALID_REPLY_TEXT.size()));
            // 清除本轮会话数据，并发送WebSocket消息告知前端本轮轮次减一
            WebSocketUtils.sendToUserBySubDestination(userId.toString(), AudioConstant.AUDIO_ROUND, 0);
            // config 存放无效对话标识，用于后续检查点数据的清除
            Optional<Object> invalidConversationInfoOpl = config.metadata(GraphConstant.INVALID_CONVERSATION_INFO);
            AtomicBoolean invalidConversationInfo = (AtomicBoolean) invalidConversationInfoOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.CONVERSATION_METADATA_NOT_CONFIGURED));
            invalidConversationInfo.set(true);
        } else if(audioTextData == null){
            log.error("[TTS] 文本数据不存在");
            throw new BusinessException(ConversationExceptionEnum.AUDIO_TEXT_DATA_NOT_EXIST);
        }


        Optional<Object> audioFluxOpl = config.metadata(GraphConstant.AUDIO_FLUX);
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
//                        log.debug("[TTS] 发送音频块，大小：{} bytes", audioData.length);
                        // 发送音频消息
                        outputStream.write(audioData); // 直接写入前端流式输出
                        outputStream.flush(); // 强制刷新，实时传输
                    } catch (Exception e) {
                        log.error("[TTS] 写入流失败", e);
                    }
                }
            }

            @Override
            public void onComplete() {
                log.info("[TTS] 音频流式输出完成");
                // 清理中断对象
                TtsStreamHolder.clear(userId);
                // 发送完成消息
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                log.error("[TTS] 音频流处理异常", e);
                // 清理中断对象
                TtsStreamHolder.clear(userId);

                // 发送错误消息
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
            synthesizer.call(audioTextData);
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
}
