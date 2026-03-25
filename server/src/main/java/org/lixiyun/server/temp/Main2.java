package org.lixiyun.server.temp;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class TimeUtils {
    // 私有静态常量：时间格式化规则（年-月-日 时:分:秒.毫秒）
    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // 公共静态方法：对外提供「当前时间戳字符串」
    public static String getTimestamp() {
        return LocalDateTime.now().format(formatter); // 获取当前时间 → 按规则格式化
    }
}

public class Main2 {
    // main方法：Java程序启动后第一个执行的方法，args是命令行参数
    public static void main(String[] args) throws InterruptedException {
        // 1. 创建【单线程池】：只开1个线程执行任务，避免多线程冲突
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        // 2. 提交异步识别任务：
        // 音频路径 = 项目根目录 + asr_example.wav（需要提前把音频放在这里）
        executorService.submit(new RealtimeRecognitionTask1(Paths.get(System.getProperty("user.dir"), "asr_example.wav")));

        // 3. 关闭线程池：拒绝接收新任务，已提交的任务继续执行
        executorService.shutdown();

        // 4. 等待线程池所有任务执行完成，最长等待1分钟
        executorService.awaitTermination(1, TimeUnit.MINUTES);

        // 5. 强制退出JVM，结束整个程序
        System.exit(0);
    }
}

// 自定义任务类，实现Runnable接口 → 可交给线程池执行
class RealtimeRecognitionTask1 implements Runnable {
    // 成员变量：存储音频文件的路径
    private Path filepath;

    // 构造方法：接收文件路径，赋值给成员变量
    public RealtimeRecognitionTask1(Path filepath) {
        this.filepath = filepath;
    }

    // 重写Runnable的run方法：线程启动后，自动执行这里的逻辑
    @Override
    public void run() {
        // ===================== 1. 配置语音识别参数 =====================
        // 建造者模式：链式调用配置识别参数（阿里云SDK规定写法）
        RecognitionParam param = RecognitionParam.builder()
                // 阿里云API Key：若未配置环境变量，取消注释并填写自己的Key
                // .apiKey("yourApikey")
                .model("paraformer-realtime-v2") // 指定识别模型：阿里云实时语音识别v2
                .format("wav") // 音频格式：仅支持wav
                .sampleRate(16000) // 音频采样率：必须16000Hz（16k）
                // 语言提示：仅v2模型支持，指定识别【中文+英文】
                .parameter("language_hints", new String[]{"zh", "en"})
                .build(); // 构建参数对象

        // 创建识别器实例：与阿里云服务交互的核心对象
        Recognition recognizer = new Recognition();

        // 获取当前执行任务的线程名称（日志用，方便排查）
        String threadName = Thread.currentThread().getName();

        // ===================== 2. 定义异步回调（接收识别结果） =====================
        // 回调：阿里云服务返回结果时，自动触发对应方法
        ResultCallback<RecognitionResult> callback = new ResultCallback<>() {
            // 触发时机：收到识别结果（中间结果/最终结果）
            @Override
            public void onEvent(RecognitionResult message) {
                // 判断是否为【句子结束】→ 最终识别结果
                if (message.isSentenceEnd()) {
                    System.out.println(TimeUtils.getTimestamp()+" "+
                            "[process " + threadName + "] Final Result:" + message.getSentence().getText());
                } else {
                    // 否则为【中间结果】→ 实时预览识别内容
                    System.out.println(TimeUtils.getTimestamp()+" "+
                            "[process " + threadName + "] Intermediate Result: " + message.getSentence().getText());
                }
            }

            // 触发时机：整个识别任务完全完成
            @Override
            public void onComplete() {
                System.out.println(TimeUtils.getTimestamp()+" "+"[" + threadName + "] Recognition complete");
            }

            // 触发时机：识别过程报错（如网络异常、音频格式错误）
            @Override
            public void onError(Exception e) {
                System.out.println(TimeUtils.getTimestamp()+" "+
                        "[" + threadName + "] RecognitionCallback error: " + e.getMessage());
            }
        };

        // ===================== 3. 执行音频读取+流式识别 =====================
        try {
            // 启动识别：建立WebSocket长连接，传入参数和回调
            recognizer.call(param, callback);

            // 打印日志：当前使用的音频文件路径
            System.out.println(TimeUtils.getTimestamp()+" "+"[" + threadName + "] Input file_path is: " + this.filepath);

            // 创建文件输入流：读取本地音频文件
            FileInputStream fis = new FileInputStream(this.filepath.toFile());

            // 音频缓冲区：16k采样率的wav，3200字节 = 1秒音频数据
            byte[] buffer = new byte[3200];
            // 存储每次读取的字节数
            int bytesRead;

            // 循环读取音频文件：直到文件读完（read返回-1）
            while ((bytesRead = fis.read(buffer)) != -1) {
                ByteBuffer byteBuffer;
                // 打印本次读取的音频字节数
                System.out.println(TimeUtils.getTimestamp()+" "+"[" + threadName + "] bytesRead: " + bytesRead);

                // 处理最后一块音频（可能不足3200字节）
                if (bytesRead < buffer.length) {
                    byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
                } else {
                    // 完整音频块，直接封装
                    byteBuffer = ByteBuffer.wrap(buffer);
                }

                // 核心：发送音频帧给阿里云识别服务
                recognizer.sendAudioFrame(byteBuffer);
                // 重置缓冲区
                buffer = new byte[3200];
                // 线程睡眠100ms：模拟【真人实时说话】的间隔（避免一次性发完所有音频）
                Thread.sleep(100);
            }

            // 音频文件读取完成，打印时间
            System.out.println(TimeUtils.getTimestamp()+" "+LocalDateTime.now());
            // 停止识别：通知阿里云服务「音频发送完毕」
            recognizer.stop();

        } catch (Exception e) {
            // 捕获所有异常，打印错误详情
            e.printStackTrace();
        } finally {
            // 最终执行：无论是否报错，都关闭WebSocket长连接
            // 1000=正常关闭码，bye=关闭原因
            recognizer.getDuplexApi().close(1000, "bye");
        }

        // ===================== 4. 打印识别性能指标 =====================
        System.out.println(
                "["
                        + threadName
                        + "][Metric] requestId: " // 阿里云请求ID
                        + recognizer.getLastRequestId()
                        + ", first package delay ms: " // 首包响应延迟（毫秒）
                        + recognizer.getFirstPackageDelay()
                        + ", last package delay ms: " // 尾包响应延迟（毫秒）
                        + recognizer.getLastPackageDelay());
    }
}