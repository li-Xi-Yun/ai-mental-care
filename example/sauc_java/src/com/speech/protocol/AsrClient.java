package com.speech.protocol;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class AsrClient {
    // 线程控制
    public volatile boolean isRunning = true;
    public final BlockingQueue<byte[]> responseQueue = new LinkedBlockingQueue<>();

    // 创建WebSocket客户端
    public OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .pingInterval(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public Request buildRequest(String url, String resourceId, String apiKey, String appKey, String accessKey) {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("X-Api-Resource-Id", resourceId)
                .header("X-Api-Connect-Id", UUID.randomUUID().toString());
        HeaderUtils.applyAuthHeaders(requestBuilder, apiKey, appKey, accessKey);
        return requestBuilder.build();
    }

    public void sendMessages(WebSocket webSocket, byte[] audioData, AudioInfo audioInfo, String payloadStr) throws Exception {
        int seq = 1;

        // 发送完整客户端请求
        sendFullClientRequest(webSocket, seq, payloadStr);

        // 计算分段大小
        int segmentSize = AudioUtils.calculateSegmentSize(audioInfo, ProtocolConstants.DEFAULT_SEGMENT_DURATION_MS);
        List<byte[]> audioSegments = AudioUtils.splitAudio(audioData, segmentSize);

        // 分片发送音频数据：按固定节拍发送，只补偿本轮发送/调度已消耗的时间，避免固定sleep造成的间隔漂移
        final long intervalNanos = TimeUnit.MILLISECONDS.toNanos(ProtocolConstants.DEFAULT_SEGMENT_DURATION_MS);
        long nextDeadlineNanos = System.nanoTime();
        for (int i = 0; i < audioSegments.size() && isRunning; i++) {
            byte[] segment = audioSegments.get(i);
            boolean isLast = (i == audioSegments.size() - 1);

            seq++;
            int finalSeq = isLast ? -seq : seq;

            System.out.println("发送音频分段: 序号 " + seq + ", 长度 " + segment.length + ", 是否最后一段: " + isLast);
            sendAudioSegment(webSocket, segment, isLast, finalSeq);

            nextDeadlineNanos += intervalNanos;
            long remainingNanos = nextDeadlineNanos - System.nanoTime();
            if (remainingNanos > 0) {
                TimeUnit.NANOSECONDS.sleep(remainingNanos);
            } else {
                // 本轮已超过一个节拍，重新对齐到当前时刻，避免落后后连续无等待突发补发
                nextDeadlineNanos = System.nanoTime();
            }
        }
    }

    public void sendFullClientRequest(WebSocket webSocket, int seq, String payloadStr) throws IOException {
        byte[] payloadBytes = ProtocolCodec.gzipCompress(payloadStr.getBytes());
        byte[] header = HeaderUtils.getHeader(MessageType.CLIENT_FULL_REQUEST.getCode(), MessageFlag.POS_SEQUENCE.getCode(),
                SerializationType.JSON.getCode(), CompressionType.GZIP.getCode(), (byte) 0);
        byte[] payloadSize = ProtocolCodec.intToBytes(payloadBytes.length);
        byte[] seqBytes = ProtocolCodec.intToBytes(seq);

        byte[] fullClientRequest = new byte[header.length + seqBytes.length + payloadSize.length + payloadBytes.length];
        System.arraycopy(header, 0, fullClientRequest, 0, header.length);
        System.arraycopy(seqBytes, 0, fullClientRequest, header.length, seqBytes.length);
        System.arraycopy(payloadSize, 0, fullClientRequest, header.length + seqBytes.length, payloadSize.length);
        System.arraycopy(payloadBytes, 0, fullClientRequest, header.length + seqBytes.length + payloadSize.length,
                payloadBytes.length);
        webSocket.send(ByteString.of(fullClientRequest));
    }

    public void sendAudioSegment(WebSocket webSocket, byte[] buffer, boolean isLast, int seq) {
        byte messageTypeSpecificFlags = isLast ? MessageFlag.NEG_WITH_SEQUENCE.getCode() : MessageFlag.POS_SEQUENCE.getCode();
        byte[] header = HeaderUtils.getHeader(MessageType.CLIENT_AUDIO_ONLY_REQUEST.getCode(), messageTypeSpecificFlags,
                SerializationType.JSON.getCode(), CompressionType.GZIP.getCode(), (byte) 0);
        byte[] sequenceBytes = ProtocolCodec.intToBytes(seq);
        byte[] payloadBytes = ProtocolCodec.gzipCompress(buffer, buffer.length);
        byte[] payloadSize = ProtocolCodec.intToBytes(payloadBytes.length);

        byte[] audioRequest = new byte[header.length + sequenceBytes.length + payloadSize.length + payloadBytes.length];
        System.arraycopy(header, 0, audioRequest, 0, header.length);
        System.arraycopy(sequenceBytes, 0, audioRequest, header.length, sequenceBytes.length);
        System.arraycopy(payloadSize, 0, audioRequest, header.length + sequenceBytes.length, payloadSize.length);
        System.arraycopy(payloadBytes, 0, audioRequest, header.length + sequenceBytes.length + payloadSize.length,
                payloadBytes.length);

        webSocket.send(ByteString.of(audioRequest));
    }

    public void processResponses(WebSocket webSocket, OkHttpClient client) {
        while (isRunning || !responseQueue.isEmpty()) {
            try {
                byte[] response = responseQueue.poll(100, TimeUnit.MILLISECONDS);
                if (response != null) {
                    AsrResponse asrResponse = AsrResponse.parseResponse(response);
                    System.out.println("收到响应: " + asrResponse);

                    if (asrResponse.isLastPackage) {
                        System.out.println("最后一个包 序号" + asrResponse.payloadSequence + "\n");
                        isRunning = false;
                        // 关闭WebSocket连接
                        webSocket.close(1000, "正常关闭");
                        /// 立即关闭OkHttpClient线程池
                        ExecutorService clientExecutor = client.dispatcher().executorService();
                        clientExecutor.shutdownNow();
                        try {
                            if (!clientExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                                System.err.println("OkHttp线程池未能及时关闭");
                            }
                        } catch (InterruptedException e) {
                            clientExecutor.shutdownNow();
                            Thread.currentThread().interrupt();
                        }
                    }

                    if (asrResponse.code != 0) {
                        System.err.println("服务器返回错误: " + asrResponse.payloadMsg);
                        isRunning = false;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("处理响应时出错: " + e.getMessage());
            }
        }
    }
}
