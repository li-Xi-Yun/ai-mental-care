package org.lixiyun.server.temp;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import io.reactivex.Flowable;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    // 不同模型版本需要使用对应版本的音色：
    // cosyvoice-v3-flash/cosyvoice-v3-plus：使用longanyang等音色。
    // cosyvoice-v2：使用longxiaochun_v2等音色。
    // 每个音色支持的语言不同，合成日语、韩语等非中文语言时，需选择支持对应语言的音色。详见CosyVoice音色列表。
    private static String model = "cosyvoice-v3-flash";
    private static String voice = "longanyang";
    public static void process() throws NoApiKeyException, InputRequiredException {
        // Playback thread
        class PlaybackRunnable implements Runnable {
            // Set the audio format. Please configure according to your actual device,
            // synthesized audio parameters, and platform choice Here it is set to
            // 22050Hz16bit single channel. It is recommended that customers choose other
            // sample rates and formats based on the model sample rate and device
            // compatibility.
            private AudioFormat af = new AudioFormat(22050, 16, 1, true, false);
            private DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
            private SourceDataLine targetSource = null;
            private AtomicBoolean runFlag = new AtomicBoolean(true);
            private ConcurrentLinkedQueue<ByteBuffer> queue =
                    new ConcurrentLinkedQueue<>();

            // Prepare the player
            public void prepare() throws LineUnavailableException {
                targetSource = (SourceDataLine) AudioSystem.getLine(info);
                targetSource.open(af, 4096);
                targetSource.start();
            }

            public void put(ByteBuffer buffer) {
                queue.add(buffer);
            }

            // Stop playback
            public void stop() {
                runFlag.set(false);
            }

            @Override
            public void run() {
                if (targetSource == null) {
                    return;
                }

                while (runFlag.get()) {
                    if (queue.isEmpty()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                        continue;
                    }

                    ByteBuffer buffer = queue.poll();
                    if (buffer == null) {
                        continue;
                    }

                    byte[] data = buffer.array();
                    targetSource.write(data, 0, data.length);
                }

                // Play all remaining cache
                if (!queue.isEmpty()) {
                    ByteBuffer buffer = null;
                    while ((buffer = queue.poll()) != null) {
                        byte[] data = buffer.array();
                        targetSource.write(data, 0, data.length);
                    }
                }
                // Release the player
                targetSource.drain();
                targetSource.stop();
                targetSource.close();
            }
        }

        // Create a subclass inheriting from ResultCallback<SpeechSynthesisResult>
        // to implement the callback interface
        class ReactCallback extends ResultCallback<SpeechSynthesisResult> {
            private PlaybackRunnable playbackRunnable = null;

            public ReactCallback(PlaybackRunnable playbackRunnable) {
                this.playbackRunnable = playbackRunnable;
            }

            // Callback when the service side returns the streaming synthesis result
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                // Get the binary data of the streaming result via getAudio
                if (result.getAudioFrame() != null) {
                    // Stream the data to the player
                    playbackRunnable.put(result.getAudioFrame());
                }
            }

            // Callback when the service side completes the synthesis
            @Override
            public void onComplete() {
                // Notify the playback thread to end
                playbackRunnable.stop();
            }

            // Callback when an error occurs
            @Override
            public void onError(Exception e) {
                // Tell the playback thread to end
                System.out.println(e);
                playbackRunnable.stop();
            }
        }

        PlaybackRunnable playbackRunnable = new PlaybackRunnable();
        try {
            playbackRunnable.prepare();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
        Thread playbackThread = new Thread(playbackRunnable);
        // Start the playback thread
        playbackThread.start();
        /*******  Call the Generative AI Model to get streaming text *******/
        // Prepare for the LLM call
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("请介绍一下你自己")
                .build();
        GenerationParam genParam =
                GenerationParam.builder()
                        // 若没有将API Key配置到环境变量中，需将下面这行代码注释放开，并将apiKey替换为自己的API Key
                        // .apiKey("apikey")
                        .model("qwen-turbo")
                        .messages(Arrays.asList(userMsg))
                        .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                        .topP(0.8)
                        .incrementalOutput(true)
                        .build();
        // Prepare the speech synthesis task
        SpeechSynthesisParam param =
                SpeechSynthesisParam.builder()
                        // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                        // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                        .model(model)
                        .voice(voice)
                        .format(SpeechSynthesisAudioFormat
                                .PCM_22050HZ_MONO_16BIT)
                        .build();
        SpeechSynthesizer synthesizer =
                new SpeechSynthesizer(param, new ReactCallback(playbackRunnable));
        Flowable<GenerationResult> result = gen.streamCall(genParam);
        result.blockingForEach(message -> {
            String text = message.getOutput().getChoices().get(0).getMessage().getContent().trim();
            if (text != null && !text.isEmpty()) {
                System.out.println("LLM output：" + text);
                synthesizer.streamingCall(text);
            }
        });
        synthesizer.streamingComplete();
        System.out.print("requestId: " + synthesizer.getLastRequestId());
        try {
            // Wait for the playback thread to finish playing all
            playbackThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws NoApiKeyException, InputRequiredException {
        // 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
        process();
        System.exit(0);
    }
}