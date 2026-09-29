import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.speech.protocol.AsrClient;
import com.speech.protocol.AsrResponse;
import com.speech.protocol.AudioInfo;
import com.speech.protocol.AudioUtils;
import com.speech.protocol.CompressionType;
import com.speech.protocol.HeaderUtils;
import com.speech.protocol.MessageFlag;
import com.speech.protocol.MessageType;
import com.speech.protocol.ProtocolConstants;
import com.speech.protocol.ProtocolCodec;
import com.speech.protocol.SerializationType;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.sound.sampled.*;
import okhttp3.*;
import okio.ByteString;

public class sauc {
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public static void main(String[] args) throws Exception {
        String url = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async";
        String apiKey = "";
        String appKey = "";
        String accessKey = "";
        String resourceId = "volc.bigasr.sauc.duration";
        String audioFilePath = "";

        // 支持命名参数；同时兼容老用法: url appId token audioFilePath
        if (args.length > 0 && !args[0].startsWith("--")) {
            if (args.length != 4) {
                System.err.println("Usage: --url=<url> --api_key=<api_key> --audio_file=<path> [--resource_id=<resource_id>] "
                        + "| --url=<url> --app_key=<app_key> --access_key=<access_key> --audio_file=<path> [--resource_id=<resource_id>] "
                        + "| <url> <appId> <token> <audioFilePath>");
                System.exit(1);
            }
            url = args[0];
            appKey = args[1];
            accessKey = args[2];
            audioFilePath = args[3];
        } else {
            for (String arg : args) {
                if (arg.startsWith("--url=")) url = arg.substring(6);
                else if (arg.startsWith("--api_key=")) apiKey = arg.substring(10);
                else if (arg.startsWith("--app_key=")) appKey = arg.substring(10);
                else if (arg.startsWith("--access_key=")) accessKey = arg.substring(13);
                else if (arg.startsWith("--resource_id=")) resourceId = arg.substring(14);
                else if (arg.startsWith("--audio_file=")) audioFilePath = arg.substring(13);
            }
        }

        if (url.isEmpty() || audioFilePath.isEmpty()) {
            System.err.println("url and audio_file are required");
            System.exit(1);
        }
        if (apiKey.isEmpty() && (appKey.isEmpty() || accessKey.isEmpty())) {
            System.err.println("auth required: provide --api_key, or --app_key + --access_key");
            System.exit(1);
        }

        // 创建WebSocket客户端
        AsrClient asr = new AsrClient();
        OkHttpClient client = asr.createHttpClient();
        Request request = asr.buildRequest(url, resourceId, apiKey, appKey, accessKey);

        WebSocket webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("===> 连接已建立, X-Tt-Logid:" + response.header("X-Tt-Logid"));
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                System.out.println("===> 收到文本消息: " + text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                asr.responseQueue.offer(bytes.toByteArray());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                System.out.println("===> 连接正在关闭: code=" + code + ", reason=" + reason);
                asr.isRunning = false;
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                System.err.println("===> 连接失败: " + t.getMessage());
                asr.isRunning = false;
            }
        });

        // 处理音频文件
        try {
            // 读取完整WAV文件数据
            byte[] fullData = AudioUtils.readAudioData(audioFilePath);
            System.out.println("读取音频文件完成，总大小: " + fullData.length + " 字节");
            
            // 解析WAV信息
            AudioInfo audioInfo = AudioUtils.parseWavInfo(fullData);
            
            System.out.println("音频信息: 采样率=" + audioInfo.sampleRate + 
                             ", 声道数=" + audioInfo.channels + 
                             ", 位深=" + audioInfo.bitsPerSample);
            
            CountDownLatch latch = new CountDownLatch(2);
            
            // 启动发送线程
            executor.submit(() -> {
                try {
                    String payloadStr = buildFullClientRequestPayload();
                    asr.sendMessages(webSocket, fullData, audioInfo, payloadStr);
                } catch (Exception e) {
                    System.err.println("发送线程出错: " + e.getMessage());
                    asr.isRunning = false;
                } finally {
                    latch.countDown();
                }
            });

            // 启动接收线程
            executor.submit(() -> {
                try {
                    asr.processResponses(webSocket, client);
                } catch (Exception e) {
                    System.err.println("接收线程出错: " + e.getMessage());
                    asr.isRunning = false;
                } finally {
                    latch.countDown();
                }
            });

            // 主线程等待两个子线程执行完毕
            latch.await();
        } catch (Exception e) {
            System.err.println("音频处理失败: " + e.getMessage());
        } finally {
            // 关闭 WebSocket
            if (webSocket != null) {
                webSocket.close(1000, "正常关闭");
            }
            // 关闭自定义线程池
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("线程中断: " + e.getMessage());
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            // 关闭 OkHttpClient 线程池
            client.dispatcher().executorService().shutdown();
            try {
                if (!client.dispatcher().executorService().awaitTermination(30, TimeUnit.SECONDS)) {
                    client.dispatcher().executorService().shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("线程中断: " + e.getMessage());
                client.dispatcher().executorService().shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.err.println("资源完成关闭");
        }
    }

    // 构造完整客户端请求(首包)的 JSON payload，返回其字符串形式供发送侧压缩组帧
    private static String buildFullClientRequestPayload() {
        JsonObject user = new JsonObject();
        user.addProperty("uid", "demo_uid");

        JsonObject audio = new JsonObject();
        audio.addProperty("format", "wav");
        audio.addProperty("codec", "raw");
        // 与Go版本保持一致，使用硬编码的音频参数
        audio.addProperty("rate", 16000);
        audio.addProperty("bits", 16);
        audio.addProperty("channel", 1);

        JsonObject request = new JsonObject();
        request.addProperty("model_name", "bigmodel");
        request.addProperty("enable_itn", true);
        request.addProperty("enable_punc", true);
        request.addProperty("enable_ddc", true);
        request.addProperty("show_utterances", true);
        request.addProperty("enable_nonstream", false);

        JsonObject payload = new JsonObject();
        payload.add("user", user);
        payload.add("audio", audio);
        payload.add("request", request);

        String payloadStr = payload.toString();
        //这里可以使用JSON复刻payloadStr
        System.out.println("发送完整客户端请求: " + payloadStr);
        return payloadStr;
    }
}