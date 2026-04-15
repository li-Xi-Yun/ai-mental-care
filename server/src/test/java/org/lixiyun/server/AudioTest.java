package org.lixiyun.server;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioSpeechApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechOptions;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.lixiyun.file.storage.FileStorage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * @author lixiyun
 * @since 2026-04-01 10:05
 */
@Slf4j
@SpringBootTest
public class AudioTest {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Autowired
    private FileStorage fileStorage;

    private String text = "我没有听清你的讲什么，能再讲一次吗";

    @Test
    public void test1() {
        log.info("语音输出节点：开始执行");

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

        Flux<byte[]> audioByteStream = dashScopeAudioSpeechModel.stream(text, speechOptions);

        ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
//        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024 * 5);  // 5MB

        // 流式发送音频数据到 WebSocket
        Disposable subscribe = audioByteStream
                .publishOn(Schedulers.boundedElastic())
                .subscribe(audioChunk -> {
                    log.debug("[TTS] 发送音频块，大小：{} bytes", audioChunk.length);
//                    buffer.put(audioChunk);
                    try {
                        audioStream.write(audioChunk);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, error -> {
                    log.error("[TTS] 音频流处理异常", error);
                }, () -> {
                    log.info("[TTS] 音频流式输出完成");
                    fileStorage.localBinaryStorage(audioStream.toByteArray(), "webm" , FileStorage.StorageType.AUDIO);
                });
    }


    @Test
    public void test2() {
        // 模型
        String model = "cosyvoice-v3-flash";
        // 音色
        String voice = "longanyang";

//        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
        ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);

        // 实现回调接口ResultCallback
        ResultCallback<SpeechSynthesisResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                // System.out.println("收到消息: " + result);
                if (result.getAudioFrame() != null) {
                    // 此处实现保存音频数据到本地的逻辑
                    byte[] audioData = result.getAudioFrame().array();
//                    buffer.put(audioData);
                    try {
                        audioStream.write(audioData);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    log.info("收到音频:{}字节", audioData.length);
                }
            }

            @Override
            public void onComplete() {
                System.out.println("收到Complete，语音合成结束");
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                System.out.println("出现异常：" + e.toString());
                latch.countDown();
            }
        };

        // 请求参数
        SpeechSynthesisParam param =
                SpeechSynthesisParam.builder()
                        // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                        // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                        .apiKey(apiKey)
                        .model(model) // 模型
                        .voice(voice) // 音色
                        .build();
        // 第二个参数“callback”传入回调即启用异步模式
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, callback);
        // 非阻塞调用，立即返回null（实际结果通过回调接口异步传递），在回调接口的onEvent方法中实时获取二进制音频
        try {
            synthesizer.call(text);
            log.debug("模型执行成功");
            // 等待合成完成
            latch.await();
            // 等待播放线程全部播放完
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 任务结束后关闭websocket连接
            synthesizer.getDuplexApi().close(1000, "bye");
        }
        // 首次发送文本时需建立 WebSocket 连接，因此首包延迟会包含连接建立的耗时
        System.out.println(
                "[Metric] requestId为："
                        + synthesizer.getLastRequestId()
                        + "，首包延迟（毫秒）为："
                        + synthesizer.getFirstPackageDelay());

        byte[] byteArray = audioStream.toByteArray();
        fileStorage.localBinaryStorage(byteArray, "webm" , FileStorage.StorageType.AUDIO);
        log.info("语音输出节点：执行完成-总字节数：{}", byteArray.length);
    }


    @Autowired
    @Qualifier("dashScopeChatModel")
    private ChatModel chatModel;

    @Test
    public void test3() {
        String model = "cosyvoice-v3-flash";
        DashScopeChatOptions build = DashScopeChatOptions.builder()
                .model(model)
                .temperature(0.1)        // 极低随机性（0-2），避免情绪标签/置信度波动，确保诊断结果一致
                .topP(0.8)               // 聚焦高概率词汇（0-1），减少无意义输出，贴合青年语境
                .topK(40)                // 限制候选词范围（1-100），提升输出确定性，避免无效标签
                .seed(42)                // 固定随机种子，保证相同输入生成一致的情绪诊断结果
                .maxToken(3000)          // 足够容纳完整JSON输出（含1000字诊断内容+多维度量化数据）
                .repetitionPenalty(1.2)  // 惩罚重复token（默认1.1），避免"焦虑、焦虑"这类重复标签
                .responseFormat(DashScopeResponseFormat.builder()
                        .type(DashScopeResponseFormat.Type.JSON_OBJECT)  // 强制输出合法JSON对象
                        .build())
                .enableThinking(true)    // 启用模型思考模式，深入理解"摆烂/emo/内卷"等青年专属语境
                .thinkingBudget(5)       // 思考预算（1-10），平衡分析深度与响应速度
                .enableSearch(true)     // 情绪分析无需联网搜索，禁用减少响应时间
                .stream(false)           // 非流式输出，直接获取完整JSON结果
                .incrementalOutput(false)// 关闭增量输出，保证结果完整性
                .multiModel(false)       // 单模型即可满足需求，禁用多模型调度
                .vlHighResolutionImages(false) // 禁用视觉相关配置，减少资源消耗
                .build();

        ReactAgent agent = ReactAgent.builder()
                .model(chatModel)
                .chatOptions(build)
                .enableLogging(false)
                .build();
        try {
            AssistantMessage call = agent.call(text);
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }


}
