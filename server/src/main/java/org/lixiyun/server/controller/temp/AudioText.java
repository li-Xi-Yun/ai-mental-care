package org.lixiyun.server.controller.temp;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.server.infrastructure.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Sinks;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * @author lixiyun
 * @since 2026-03-31 20:43
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/temp")
@Tag(name = "语音测试相关接口", description = "语音测试相关接口")
public class AudioText {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    private final FileStorage fileStorage;

    // 在这里写一个接口，接收前端发送的语音数据信息，进行文件本地存储
    @PostMapping(value = "/audio-text", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "语音转文本", description = "接收前端录制的原始语音二进制流")
    public Result<String> audioUpload(@RequestBody byte[] audioData) throws InterruptedException {
        log.info("语音转文本:{}", audioData.length);
        String filePath = fileStorage.localBinaryStorage(audioData, "mp3", FileStorage.StorageType.AUDIO);
        test(audioData);
        return Result.success(filePath);
    }

    private void test(byte[] audioData) {
        RecognitionParam param = RecognitionParam.builder()
                .apiKey(apiKey)
                .model("paraformer-realtime-8k-v2")
                .format("mp3")
                .sampleRate(8000)
                .build();
        Recognition recognizer = new Recognition();

        String threadName = Thread.currentThread().getName();
        ResultCallback<RecognitionResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(RecognitionResult message) {
                log.info("识别开始：{}、{}", message.getSentence().getText(), message);
                if (message.isSentenceEnd()) {
                    log.info("识别过程-结果：{}、{}", message.getSentence().getText(), message);
                } else {
                    log.info("识别完成-结果：{}、{}", message.getSentence().getText(), message);
                }
            }

            @Override
            public void onComplete() {
                log.info("模型调用完成");
            }

            @Override
            public void onError(Exception e) {
                log.error("模型调用异常");
            }
        };

        try (ByteArrayInputStream bis = new ByteArrayInputStream(audioData)){
            recognizer.call(param, callback);
            byte[] buffer = new byte[3200];
            int bytesRead;
            // 循环读取音频二进制流，发送给识别模型
            while ((bytesRead = bis.read(buffer)) != -1) {

//                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
//                recognizer.sendAudioFrame(byteBuffer);
                ByteBuffer byteBuffer;
                System.out.println("bytesRead: " + bytesRead);
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
            log.error("语音识别执行异常", e);
        } finally {
            // 任务结束后关闭 Websocket 连接
            recognizer.getDuplexApi().close(1000, "bye");
        }

        System.out.println(
                "[" + threadName + "][Metric] requestId: " + recognizer.getLastRequestId()
                        + ", 首包延迟(ms): " + recognizer.getFirstPackageDelay()
                        + ", 尾包延迟(ms): " + recognizer.getLastPackageDelay());
    }

    @PostMapping(value = "/local-file")
    @Operation(summary = "本地文件识别", description = "本地文件识别")
    public Result<String> localFileUpload() {
        log.info("本地文件识别");
        test2();
        return Result.success();
    }

    private void test2() {
        RecognitionParam param = RecognitionParam.builder()
                .apiKey(apiKey)
                .model("paraformer-realtime-8k-v2")
                .format("mp3")
                .sampleRate(8000)
                .build();
        Recognition recognizer = new Recognition();

        String threadName = Thread.currentThread().getName();
        ResultCallback<RecognitionResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(RecognitionResult message) {
                log.info("识别开始：{}、{}", message.getSentence().getText(), message);
                if (message.isSentenceEnd()) {
                    log.info("识别过程-结果：{}、{}", message.getSentence().getText(), message);
                } else {
                    log.info("识别完成-结果：{}、{}", message.getSentence().getText(), message);
                }
            }

            @Override
            public void onComplete() {
                log.info("模型调用完成");
            }

            @Override
            public void onError(Exception e) {
                log.error("模型调用异常");
            }
        };

        try {
            recognizer.call(param, callback);
            String filePath = "D:\\JavaProject\\ai-mental-care\\audios\\20260401b7390781-f8a5-46d4-be43-44f7ae66c17f.mp3";
            FileInputStream fis = new FileInputStream(filePath);

            byte[] buffer = new byte[3200];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                ByteBuffer byteBuffer;
                System.out.println("bytesRead: " + bytesRead);
                if (bytesRead < buffer.length) {
                    byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
                } else {
                    byteBuffer = ByteBuffer.wrap(buffer);
                }

                recognizer.sendAudioFrame(byteBuffer);
                buffer = new byte[3200];
                Thread.sleep(100);
            }
            log.info("任务结束");
            recognizer.stop();
        } catch (Exception e) {
            log.error("语音识别执行异常", e);
        } finally {
            // 任务结束后关闭 Websocket 连接
            recognizer.getDuplexApi().close(1000, "bye");
        }

        System.out.println(
                "[" + threadName + "][Metric] requestId: " + recognizer.getLastRequestId()
                        + ", 首包延迟(ms): " + recognizer.getFirstPackageDelay()
                        + ", 尾包延迟(ms): " + recognizer.getLastPackageDelay());
    }


    @PostMapping(value = "/text-audio")
    @Operation(summary = "文本转语音", description = "文本转语音")
    public Result<String> audioToText() {
        log.info("文本转语音");
        test3();
        return Result.success();
    }

    public void test3() {
        String text = "我没有听清你的讲什么，能再讲一次吗";

        // 模型
        String model = "cosyvoice-v3-flash";
        // 音色
        String voice = "longanyang";

        ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);

        // 实现回调接口ResultCallback
        ResultCallback<SpeechSynthesisResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                if (result.getAudioFrame() != null) {
                    // 此处实现保存音频数据到本地的逻辑
                    byte[] audioData = result.getAudioFrame().array();
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


//    @PostMapping(value = "/text-audio-stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
//    @Operation(summary = "文本转语音(流式返回)", description = "文本转语音(流式返回)")
//    public Flux<byte[]> audioToTextStream() {
//        log.info("文本转语音");
//        Sinks.Many<byte[]> audioSink = Sinks.many().unicast().onBackpressureBuffer();  // 流式返回值
//        new Thread(() -> {
//            test4(audioSink);
//        }).start();
//        return audioSink.asFlux();
//    }

    @PostMapping(value = "/text-audio-stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "文本转语音(流式返回)", description = "文本转语音(流式返回)")
    public StreamingResponseBody audioToTextStream() {
        log.info("文本转语音（Spring MVC 原生流式）");

        // 直接返回流式响应体（Spring MVC 原生支持，无任何报错）
        return outputStream -> {
            try {
                test4(outputStream); // 把输出流传给语音合成方法
            } catch (Exception e) {
                log.error("流式输出异常", e);
            }
        };
    }

    public void test4(OutputStream outputStream) {
        String text = "我没有听清你的讲什么，能再讲一次吗";
        String model = "cosyvoice-v3-flash";
        String voice = "longanyang";
        CountDownLatch latch = new CountDownLatch(1);

        // 回调接口：实时把音频数据写入响应流
        ResultCallback<SpeechSynthesisResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                if (result.getAudioFrame() != null) {
                    try {
                        byte[] audioData = result.getAudioFrame().array();
                        outputStream.write(audioData); // 🔥 直接写入前端流式输出
                        outputStream.flush(); // 强制刷新，实时传输
                        log.info("实时推送音频:{}字节", audioData.length);
                    } catch (Exception e) {
                        log.error("写入流失败", e);
                    }
                }
            }

            @Override
            public void onComplete() {
                log.info("语音合成结束");
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                log.error("合成异常", e);
                latch.countDown();
            }
        };

        // 原有语音合成逻辑（完全不变）
        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(model)
                .voice(voice)
                .build();

        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, callback);

        try {
            synthesizer.call(text);
            latch.await(); // 等待合成完成
            log.debug("模型执行成功");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                outputStream.close(); // 关闭流
                synthesizer.getDuplexApi().close(1000, "bye");
            } catch (Exception e) {
                log.error("关闭资源失败", e);
            }
        }
    }

    public void test5(Sinks.Many<byte[]> audioSink) {
        String text = "我没有听清你的讲什么，能再讲一次吗";

        // 模型
        String model = "cosyvoice-v3-flash";
        // 音色
        String voice = "longanyang";

        ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);

        // 实现回调接口ResultCallback
        ResultCallback<SpeechSynthesisResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                if (result.getAudioFrame() != null) {
                    // 此处实现保存音频数据到本地的逻辑
                    byte[] audioData = result.getAudioFrame().array();
                    audioSink.tryEmitNext(audioData);
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
                audioSink.tryEmitComplete();
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                System.out.println("出现异常：" + e.toString());
                audioSink.tryEmitError(e);
                latch.countDown();
            }
        };

        // 请求参数
        SpeechSynthesisParam param =
                SpeechSynthesisParam.builder()
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
                "[Metric] requestId为：" + synthesizer.getLastRequestId()
                        + "，首包延迟（毫秒）为：" + synthesizer.getFirstPackageDelay());

        log.info("语音输出节点：执行完成-总字节数");
        byte[] byteArray = audioStream.toByteArray();
        fileStorage.localBinaryStorage(byteArray, "mp3" , FileStorage.StorageType.AUDIO);
        log.info("语音输出节点：执行完成-总字节数：{}", byteArray.length);
    }





}
