package com.alibaba.nls.client;

import java.io.File;
import java.io.FileInputStream;

import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizer;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizerListener;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizerResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * for demo show only
 */
public class SpeechRecognizerDemo {
    private static final Logger logger = LoggerFactory.getLogger(SpeechRecognizerDemo.class);
    private String appKey;
    NlsClient client;

    public SpeechRecognizerDemo(String appKey, String token, String url) {
        this.appKey = appKey;
        // Create an NlsClient object. You can globally create an NlsClient object and specify the endpoint.
        if(url.isEmpty()) {
            client = new NlsClient("wss://nls-gateway-ap-southeast-1.aliyuncs.com/ws/v1", token);
        }else {
            client = new NlsClient(url, token);
        }
    }

    // user-define params
    private static SpeechRecognizerListener getRecognizerListener(int myOrder, String userParam) {
        SpeechRecognizerListener listener = new SpeechRecognizerListener() {
            // Return intermediate results. The server returns this message when it recognizes a word.
            // This message is returned only when the setEnableIntermediateResult parameter is set to true.
            @Override
            public void onRecognitionResultChanged(SpeechRecognizerResponse response) {
                // The message name RecognitionResultChanged.
                // The status code. The code 20000000 indicates that the request is successful
                // The recognized text.
                System.out.println("name: " + response.getName() + ", status: " + response.getStatus() + ", result: " + response.getRecognizedText());
            }

            //Indicate that the recognition is completed.
            @Override
            public void onRecognitionCompleted(SpeechRecognizerResponse response) {
                System.out.println("name: " + response.getName() + ", status: " + response.getStatus() + ", result: " + response.getRecognizedText());
            }

            @Override
            public void onStarted(SpeechRecognizerResponse response) {
                System.out.println("myOrder: " + myOrder + "; myParam: " + userParam + "; task_id: " + response.getTaskId());
            }

            @Override
            public void onFail(SpeechRecognizerResponse response) {
                // response.getStatus() : the error message.
                // task_id : very important, unique id
                System.out.println("task_id: " + response.getTaskId() + ", status: " + response.getStatus() + ", status_text: " + response.getStatusText());
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

    public void process(String filepath, int sampleRate) {
        SpeechRecognizer recognizer = null;
        try {
            String myParam = "user-param";
            int myOrder = 1234;
            SpeechRecognizerListener listener = getRecognizerListener(myOrder, myParam);

            recognizer = new SpeechRecognizer(client, listener);
            recognizer.setAppKey(appKey);

            //audo format
            recognizer.setFormat(InputFormatEnum.PCM);
            // Specify the audio coding format.
            if(sampleRate == 16000) {
                recognizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            } else if(sampleRate == 8000) {
                recognizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_8K);
            }
            //intermediate result
            recognizer.setEnableIntermediateResult(true);

            long now = System.currentTimeMillis();
            recognizer.start();
            logger.info("ASR start latency : " + (System.currentTimeMillis() - now) + " ms");

            File file = new File(filepath);
            FileInputStream fis = new FileInputStream(file);
            byte[] b = new byte[3200];
            int len;
            while ((len = fis.read(b)) > 0) {
                logger.info("send data pack length: " + len);
                recognizer.send(b);

                // if it is real-time speech, then no sleep, if it is 8k sample rate, the second parameter is changed to 8000
                // if 8000 sample rate, 3200 bytes is recommended for sleep 200ms. if 16000 sample rate, 3200 bytes is recommended for sleep 100ms.
                int deltaSleep = getSleepDelta(len, sampleRate);
                Thread.sleep(deltaSleep);
            }

            now = System.currentTimeMillis();
            logger.info("ASR wait for complete");
            recognizer.stop();
            logger.info("ASR stop latency : " + (System.currentTimeMillis() - now) + " ms");

            fis.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            //close
            if (null != recognizer) {
                recognizer.close();
            }
        }
    }

    public void shutdown() {
        client.shutdown();
    }

    public static void main(String[] args) throws Exception {
        String appKey = null;
        String token = null;
        String url = ""; // default：wss://nls-gateway-ap-southeast-1.aliyuncs.com/ws/v1

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

        SpeechRecognizerDemo demo = new SpeechRecognizerDemo(appKey, token, url);
        demo.process("./nls-sample-16k.wav", 16000);
        demo.shutdown();
    }
}
