package org.lixiyun.server.controller.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.aop.annotation.RateLimit;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.conversation.AudioModelDTO;
import org.lixiyun.server.infrastructure.audio.AudioCache;
import org.lixiyun.server.infrastructure.audio.TtsStreamHolder;
import org.lixiyun.server.infrastructure.storage.FileStorage;
import org.lixiyun.server.service.AudioService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;


/**
 * @author lixiyun
 * @since 2026-03-31 22:15
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/conversaion/audio")
@Tag(name = "语音对话相关接口", description = "语音对话相关接口")
public class AudioController {

    private final AudioService audioService;

    @PostMapping("/start")
    @Operation(summary = "初始化音频数据缓存区", description = """
            当用户点击语音对话按钮时，进行发送，当出现报错时，说明已有一个语音对话窗口，不能多开一个语音对话窗口
            """)
    public Result<?> audioStart() {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("开始初始化音频数据缓存区（STOMP）：{}", currentId);
        AudioCache.initAudioCache(currentId);
        return Result.success();
    }

    private final FileStorage fileStorage;
//    @PostMapping(value ="/data", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PostMapping(value ="/data")
    @Operation(summary = "接收音频数据", description = """
            用户停止输入后2秒，发送请求
            当用户连续输入超过30秒后，强制发送请求
            """)
    public Result<?> audioData(@RequestBody byte[] audioData) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.debug("接收音频数据块（STOMP），currentId: {}, 大小：{} bytes", currentId, audioData.length);
        // 存储音频数据到缓存区
        AudioCache.addAudioData(currentId, audioData);
        String audioUrl = fileStorage.localBinaryStorage(audioData, "mp3", FileStorage.StorageType.AUDIO);
        return Result.success();
    }

    @PostMapping(value = "/model", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(type = RateLimit.RateLimitType.USER, key = "emotion-audio", maxRequests = 1, windowSizeInMillis = 1000)
    @Operation(summary = "音频 AI 模型处理", description = """
            客户端发送完整音频数据后调用此接口，触发 AI 处理流程：ASR → 情感识别 → 诊断 → 回答 → TTS → 语音数据流式返回
            用户停止输入后3秒，发送请求
            当用户连续输入超过30秒后，强制发送请求（需在发送‘接收音频数据接口’后发送）
            """)
    public StreamingResponseBody audioModel(@RequestBody @Validated AudioModelDTO audioModelDTO) {
        log.info("音频模型调用（STOMP）: {}", audioModelDTO);
        // 直接返回流式响应体（Spring MVC 原生支持，无任何报错）
        return outputStream -> {
            try {
                audioService.audioModel(audioModelDTO, outputStream);
            } catch (Exception e) {
                log.error("流式输出异常", e);
            }
        };
    }
//                test4(outputStream); // 把输出流传给语音合成方法

//        StreamingResponseBody result = ;
//        throw new RuntimeException("未实现");
//        return result;

    @PostMapping("/stop")
    @Operation(summary = "结束音频功能", description = "客户端结束语音对话时调用此接口，清理音频缓存")
    public Result<?> audioStop() {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("音频功能结束（STOMP）：{}", currentId);
        AudioCache.clearAudioCache(currentId);
        return Result.success();
    }

    @PostMapping("/interrupt")
    @Operation(summary = "中断音频流", description = "客户端需要打断 AI 回复时调用此接口")
    public Result<?> audioStreamInterrupt() {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("中断音频流式响应（STOMP）：{}", currentId);
        // 中断 TTS 流式响应
        TtsStreamHolder.interrupt(currentId);
        return Result.success();
    }
}
