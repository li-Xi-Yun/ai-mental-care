package com.alibaba.nls.client;

import java.io.File;
import java.io.FileInputStream;

import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 此示例演示了
 *      ASR实时识别API调用
 *      动态获取token
 *      通过本地模拟实时流发送
 *      识别耗时计算
 * (仅作演示，需用户根据实际情况实现)
 */
public class SpeechTranscriberDemo {
    private String appKey;
    private NlsClient client;

    private static final Logger logger = LoggerFactory.getLogger(SpeechTranscriberDemo.class);

    public SpeechTranscriberDemo(String appKey, String token, String url) {
        this.appKey = appKey;
        //Create an NlsClient object. You can globally create an NlsClient object and specify the endpoint.
        if(url.isEmpty()) {
            client = new NlsClient("wss://nls-gateway-ap-southeast-1.aliyuncs.com/ws/v1", token);
        }else {
            client = new NlsClient(url, token);
        }
    }

    private static SpeechTranscriberListener getTranscriberListener() {
        SpeechTranscriberListener listener = new SpeechTranscriberListener() {
            // Return intermediate results. The server returns this message when it recognizes a word.
            // This message is returned only when the setEnableIntermediateResult parameter is set to true.
            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() +
                    ", name: " + response.getName() +
                    //The status code. The code 20000000 indicates that the request is successful.
                    ", status: " + response.getStatus() +
                    //The sequence number of the sentence, which starts from 1.
                    ", index: " + response.getTransSentenceIndex() +
                    //The recognition result of the sentence.
                    ", result: " + response.getTransSentenceText() +
                    //The duration of currently processed audio streams, in milliseconds.
                    ", time: " + response.getTransSentenceTime());
            }

            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() + ", name: " + response.getName() + ", status: " + response.getStatus());
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() + ", name: " + response.getName() + ", status: " + response.getStatus());

            }

            //Recognize a complete sentence. The server can detect the beginning and end of a sentence. When the server detects the end of the sentence, it returns this message.
            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() +
                    ", name: " + response.getName() +
                    //The status code. The code 20000000 indicates that the request is successful.
                    ", status: " + response.getStatus() +
                    //The sequence number of the sentence, which starts from 1.
                    ", index: " + response.getTransSentenceIndex() +
                    //The recognition result of the sentence.
                    ", result: " + response.getTransSentenceText() +
                    //The confidence level.
                    ", confidence: " + response.getConfidence() +
                    //The time when the server detects the beginning of the sentence.
                    ", begin_time: " + response.getSentenceBeginTime() +
                    //The duration of currently processed audio streams, in milliseconds.
                    ", time: " + response.getTransSentenceTime());
            }

            //Indicate that the recognition is completed.
            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() + ", name: " + response.getName() + ", status: " + response.getStatus());
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() +  ", status: " + response.getStatus() + ", status_text: " + response.getStatusText());
            }
        };

        return listener;
    }

    // calculate the corresponding equivalent voice length based on the binary data size
    public static int getSleepDelta(int dataSize, int sampleRate) {
        int sampleBytes = 16;
        // only supports single channel
        int soundChannel = 1;
        return (dataSize * 10 * 8000) / (160 * sampleRate);
    }

    public void process(String filepath) {
        SpeechTranscriber transcriber = null;
        try {
            //Create an object and establish a connection
            transcriber = new SpeechTranscriber(client, getTranscriberListener());
            transcriber.setAppKey(appKey);
            //Specify the audio coding format
            transcriber.setFormat(InputFormatEnum.PCM);
            //Specify the audio sampling rate
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            //Specify whether to return intermediate results
            transcriber.setEnableIntermediateResult(false);
            //Specify whether to add punctuation marks to the recognition result
            transcriber.setEnablePunctuation(true);
            //Specify whether to enable inverse text normalization (ITN). A value of true indicates that Chinese numerals are converted to Arabic numerals
            transcriber.setEnableITN(false);

            //Serialize preceding parameters to the JSON format and send them to the server for confirmation
            transcriber.start();

            File file = new File(filepath);
            FileInputStream fis = new FileInputStream(file);
            byte[] b = new byte[3200];
            int len;
            while ((len = fis.read(b)) > 0) {
                logger.info("send data pack length: " + len);
                transcriber.send(b);
                // if it is real-time speech, then no sleep, if it is 8k sample rate, the second parameter is changed to 8000
                // if 8000 sample rate, 3200 bytes is recommended for sleep 200ms. if 16000 sample rate, 3200 bytes is recommended for sleep 100ms.
                int deltaSleep = getSleepDelta(len, 16000);
                Thread.sleep(deltaSleep);
            }

            //Notify the server that all audio data has been sent and wait for the completion message from the server.
            long now = System.currentTimeMillis();
            logger.info("ASR wait for complete");
            transcriber.stop();
            logger.info("ASR latency : " + (System.currentTimeMillis() - now) + " ms");
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            if (null != transcriber) {
                transcriber.close();
            }
        }
    }

    public void shutdown() {
        client.shutdown();
    }

    public static void main(String[] args) throws Exception {
        String appKey = null;
        String token = null;
        String url = ""; // 默认即可，默认值：wss://nls-gateway-ap-southeast-1.aliyuncs.com/ws/v1

        if (args.length == 2) {
            appKey   = args[0];
            token    = args[1];
        } else if (args.length == 3) {
            appKey   = args[0];
            token    = args[1];
            url      = args[2];
        } else {
            System.err.println("run error, need params(url is optional): " + "<app-key> <token> [url]");
            System.exit(-1);
        }

        String filepath = "nls-sample-16k.wav";
        SpeechTranscriberDemo demo = new SpeechTranscriberDemo(appKey, token, url);
        demo.process(filepath);
        demo.shutdown();
    }
}