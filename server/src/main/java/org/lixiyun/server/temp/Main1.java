package org.lixiyun.server.temp;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main1 {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(new RealtimeRecognitionTask1(Paths.get(System.getProperty("user.dir"), "asr_example.wav")));
        executorService.shutdown();
        // 阻塞主线程，等待子线程执行完毕（最多等 1 分钟）
        executorService.awaitTermination(1, TimeUnit.MINUTES);
        // 正常退出 Java 虚拟机
        System.exit(0);
    }
}

class RealtimeRecognitionTask implements Runnable {
    @Override
    public void run() {
        RecognitionParam param = RecognitionParam.builder()
                // 若没有将API Key配置到环境变量中，需将apiKey替换为自己的API Key
                // .apiKey("yourApikey")
                .model("paraformer-realtime-8k-v2")
                // 音频格式：wav
                .format("wav")
                // 采样率：16000Hz（阿里云要求的标准采样率）
                .sampleRate(16000)
                // 语言提示：支持中文+英文（仅v2模型支持）
                .parameter("language_hints", new String[]{"zh", "en"})
                // 在持续发送静音音频的情况下，可保持与服务端的连接不中断。
                .parameter("heartbeat", true)
                .build();
        Recognition recognizer = new Recognition();

        ResultCallback<RecognitionResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(RecognitionResult result) {
                if (result.isSentenceEnd()) {
                    System.out.println("Final Result: " + result.getSentence().getText());
                } else {
                    System.out.println("Intermediate Result: " + result.getSentence().getText());
                }
            }

            @Override
            public void onComplete() {
                System.out.println("Recognition complete");
            }

            @Override
            public void onError(Exception e) {
                System.out.println("RecognitionCallback error: " + e.getMessage());
            }
        };



        try {
            recognizer.call(param, callback);

            // --------------------- 配置麦克风录音格式 ---------------------
            // 音频格式参数：16000采样率、16位、单声道、有符号、小端序（必须匹配阿里云要求）
            AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);
            // 获取麦克风录音设备：根据音频格式匹配电脑默认麦克风
            TargetDataLine targetDataLine = AudioSystem.getTargetDataLine(audioFormat);
            // 打开录音设备
            targetDataLine.open(audioFormat);
            // 开始录音
            targetDataLine.start();


            // --------------------- 读取音频数据并发送给AI ---------------------
            // 创建字节缓冲区：存储1024字节的音频二进制数据
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            // 记录录音开始时间
            long start = System.currentTimeMillis();
            // 循环录音：持续50秒（50000毫秒）
            while (System.currentTimeMillis() - start < 50000) {
                // 从麦克风读取音频数据到缓冲区
                int read = targetDataLine.read(buffer.array(), 0, buffer.capacity());
                // 如果读到了有效音频数据
                if (read > 0) {
                    // 设置缓冲区有效数据长度
                    buffer.limit(read);
                    // 把音频数据实时发送给阿里云识别服务
                    recognizer.sendAudioFrame(buffer);
                    // 重新创建空缓冲区，准备下一次读取
                    buffer = ByteBuffer.allocate(1024);
                    // 线程休眠20毫秒：降低CPU占用（麦克风录音速率慢，无需一直循环）
                    Thread.sleep(20);
                }
            }

            // 50秒到了，停止语音识别
            recognizer.stop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 任务结束后关闭 Websocket 连接
            recognizer.getDuplexApi().close(1000, "bye");
        }

        System.out.println(
                "[Metric] requestId: "
                        + recognizer.getLastRequestId()
                        + ", first package delay ms: "
                        + recognizer.getFirstPackageDelay()
                        + ", last package delay ms: "
                        + recognizer.getLastPackageDelay());
    }
}