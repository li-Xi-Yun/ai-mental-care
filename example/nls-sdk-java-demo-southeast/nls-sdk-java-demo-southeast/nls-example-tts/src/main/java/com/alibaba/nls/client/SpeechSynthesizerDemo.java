package com.alibaba.nls.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The demo of speech synthesis.
 * (for demo show only)
 */
public class SpeechSynthesizerDemo {
    private static final Logger logger = LoggerFactory.getLogger(SpeechSynthesizerDemo.class);
    private static long startTime;
    private String appKey;
    NlsClient client;

    public SpeechSynthesizerDemo(String appKey, String token) {
        this.appKey = appKey;
        // Create an NlsClient object. You can globally create an NlsClient object and specify the endpoint.
        client = new NlsClient("wss://nls-gateway-ap-southeast-1.aliyuncs.com/ws/v1", token);
    }

    private static SpeechSynthesizerListener getSynthesizerListener() {
        SpeechSynthesizerListener listener = null;
        try {
            listener = new SpeechSynthesizerListener() {
                File f=new File("tts_test.wav");
                FileOutputStream fout = new FileOutputStream(f);
                private boolean firstRecvBinary = true;

                // Speech synthesis is completed.
                @Override
                public void onComplete(SpeechSynthesizerResponse response) {
                    System.out.println("name: " + response.getName() + ", status: " + response.getStatus()+", output file :"+f.getAbsolutePath());
                }

                // The speech binary data of speech synthesis.
                @Override
                public void onMessage(ByteBuffer message) {
                    try {
                        if(firstRecvBinary) {
                            // the latency of first binary
                            firstRecvBinary = false;
                            long now = System.currentTimeMillis();
                            logger.info("tts first latency : " + (now - SpeechSynthesizerDemo.startTime) + " ms");
                        }
                        byte[] bytesArray = new byte[message.remaining()];
                        message.get(bytesArray, 0, bytesArray.length);
                        //System.out.println("write array:" + bytesArray.length);
                        fout.write(bytesArray);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onFail(SpeechSynthesizerResponse response){
                    System.out.println(
                        "task_id: " + response.getTaskId() +
                            // the code 20000000 indicates that the request is successful.
                            ", status: " + response.getStatus() +
                            // error message
                            ", status_text: " + response.getStatusText());
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
        return listener;
    }

    public void process() {
        SpeechSynthesizer synthesizer = null;
        try {
            //Create an object and establish a connection.
            synthesizer = new SpeechSynthesizer(client, getSynthesizerListener());
            synthesizer.setAppKey(appKey);
            //Specify the audio coding format of the returned audio file.
            synthesizer.setFormat(OutputFormatEnum.WAV);
            //Specify the audio sampling rate of the returned audio file.
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            //The speaker.
            synthesizer.setVoice("siyue");
            //Optional. The intonation. Value range: -500 to 500. Default value: 0.
            synthesizer.setPitchRate(100);
            //The speed. Value range: -500 to 500. Default value: 0.
            synthesizer.setSpeechRate(100);
            //Set the text to be synthesized.
            synthesizer.setText("hello world!");

            //Serialize preceding parameters to the JSON format and send them to the server for confirmation.
            long start = System.currentTimeMillis();
            synthesizer.start();
            logger.info("tts start latency " + (System.currentTimeMillis() - start) + " ms");

            SpeechSynthesizerDemo.startTime = System.currentTimeMillis();

            //Wait until the speech synthesis is completed.
            synthesizer.waitForComplete();
            logger.info("tts stop latency " + (System.currentTimeMillis() - start) + " ms");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //关闭连接
            if (null != synthesizer) {
                synthesizer.close();
            }
        }
    }

    public void shutdown() {
        client.shutdown();
    }

    public static void main(String[] args) throws Exception {
        String appKey = "your appkey";
        String token  = "your token";

        if (args.length == 2) {
            appKey   = args[0];
            token    = args[1];
        } else {
            System.err.println("run error, need params: " + "<app-key> <token>");
            System.exit(-1);
        }

        SpeechSynthesizerDemo demo = new SpeechSynthesizerDemo(appKey, token);
        demo.process();
        demo.shutdown();
    }
}
