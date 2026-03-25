package org.lixiyun.server.ai.node;

import cn.hutool.core.lang.UUID;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.constant.GraphConstant;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * 语音情感识别
 * @author lixiyun
 * @since 2026-03-24 20:56
 */
@Slf4j
@Builder
public class SerEmotionRecognitionNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "serEmotionRecognitionNode";

    private final ChatModel chatModel;

    public com.alibaba.cloud.ai.graph.agent.Builder reactAgentBuild() {
        return ReactAgent.builder()
                .model(chatModel)
                .name("asrToText")
                .description("语音转文本")
                .chatOptions(chatOptions())
                .enableLogging(false);   // 启用日志记录,这个会将Agent中的提示词和每次生成的结果都打印出来
    }

    private ChatOptions chatOptions() {
        if(chatModel == null){
            throw new BusinessException(ConversationExceptionEnum.MODEL_NOT_EXIST);
        }
        if (chatModel instanceof DashScopeChatModel){
            return DashScopeChatOptions.builder()
                    // 1. 基础模型选择（优先适配对话/共情能力的模型）
                    .model("fun-asr-realtime-2026-02-28")


                    // ASR 核心配置（语音转文本专属参数，必须配置）
                    .extraBody(Map.of(
                            "asr_options", Map.of(
                                    "format", "pcm",         // 音频格式（和前端采集一致）
                                    "sample_rate", 16000,    // 采样率
                                    "language", "zh-CN"     // 中文
                            )
                    ))

                    // 流式输出（实时ASR必须开启）
                    .stream(false)                     // 开启流式：实时返回转写结果
                    .incrementalOutput(false)          // 增量输出：流式ASR必填，只返回新增文本

                    // 4. 关闭所有聊天/视觉无关功能
                    .enableSearch(false)              // 禁用搜索
                    .multiModel(false)                // 禁用多模型
                    .vlHighResolutionImages(false)    // 禁用视觉功能
                    .enableThinking(false)            // 禁用思考模式（ASR无效）

                    .build();
        } else {
            return ChatOptions.builder()
                    .topK(40)   // 每次只看前 40 个最可能的 token
                    .topP(0.9)   // 只考虑概率最高的那些词，直到它们的总概率 ≥ 90%
                    .frequencyPenalty(0.6)  // 降低已出现 token 的再次出现概率,减少重复（如“好的好的好的”）,越低越重复
                    .presencePenalty(0.2)   // 降低任何已出现 token 的再次出现概率（只要出现过就惩罚）
                    .temperature(0.7)  // 控制输出的随机性 / 创造性,越低越保守
                    .build();
        }
    }


    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("ASR 语音转文本节点：开始执行");
        Optional<Object> voiceDataOpl = state.value(GraphConstant.INPUT);
        byte[] voiceData = (byte[]) voiceDataOpl.orElseThrow(() -> new BusinessException(ConversationExceptionEnum.AUDIO_DATA_NOT_EXIST));


        String result;
        File audioFile = null;
        try {
            // 将二进制数据转换为临时文件
            String fileNameTemp = UUID.randomUUID().toString();
            audioFile = convertBytesToFile(voiceData, "audio/" + fileNameTemp, ".wav");
            log.debug("[ASR] 临时音频文件创建成功：{}", audioFile.getAbsolutePath());

            AssistantMessage call = reactAgentBuild().build().call(UserMessage.builder()
                    .media(new Media(MimeTypeUtils.parseMimeType("audio/wav"),
                            new ClassPathResource("audio/" + fileNameTemp + ".wav")))
                    .build());
            result = call.getText();
            log.debug("[ASR] 语音识别结果：{}", result);

        } catch (Exception e) {
            log.error("[ASR] 语音识别失败", e);
            throw e;
        } finally {
            // 删除临时文件
            if (audioFile != null && audioFile.exists()) {
                try {
                    Files.deleteIfExists(audioFile.toPath());
                    log.debug("[ASR] 临时文件已删除：{}", audioFile.getAbsolutePath());
                } catch (IOException e) {
                    log.warn("[ASR] 删除临时文件失败：{}", e.getMessage());
                }
            }
        }

        UserMessage resultMessage = new UserMessage(result);
        log.debug("[ASR] 语音识别结果：{}", resultMessage);
        return Map.of(GraphConstant.INPUT, resultMessage, GraphConstant.MESSAGES, resultMessage);
    }

    /**
     * 将二进制音频数据转换为临时文件
     *
     * @param audioData 音频二进制数据
     * @param prefix 文件前缀
     * @param suffix 文件后缀
     * @return 临时文件
     * @throws IOException IO 异常
     */
    private File convertBytesToFile(byte[] audioData, String prefix, String suffix) throws IOException {
        Path tempFile = Files.createTempFile(prefix, suffix);
        try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            fos.write(audioData);
        }
        File file = tempFile.toFile();

        // 设置 JVM 退出时删除临时文件,进行兜底
        file.deleteOnExit();

        return file;
    }

}
