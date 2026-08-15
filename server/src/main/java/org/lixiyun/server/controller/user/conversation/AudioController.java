package org.lixiyun.server.controller.user.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.aop.annotation.RateLimit;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.conversation.AudioModelDTO;
import org.lixiyun.pojo.vo.user.conversation.ConversationVO;
import org.lixiyun.server.infrastructure.audio.AudioCache;
import org.lixiyun.server.infrastructure.audio.TtsStreamHolder;
import org.lixiyun.server.service.user.AudioService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;


/**
 * @author lixiyun
 * @since 2026-03-31 22:15
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/conversaion/audio")
@Tag(name = "语音对话相关接口", description = "语音对话相关接口")
public class AudioController {

    private final AudioService audioService;

    @PostMapping("/start")
    @Operation(summary = "初始化音频数据缓存区", description = """
            当用户点击语音对话按钮时，进行发送，当出现报错时，说明已有一个语音对话窗口，不能多开一个语音对话窗口
            当接口返回了成功后，前端需订阅WebSocket通道：/user/queue/audio/conversationId、/user/queue/audio/round
            conversationId:这是接收语音对话所对应的会话ID，用于后续的会话名称创建http请求的
            round：这是接收本次会话是否有效，如果无效，前端所存储的currentRound需要减一
            """)
    public Result<?> audioStart() {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("初始化音频数据缓存区：{}", currentId);
        AudioCache.initAudioCache(currentId);
        return Result.success();
    }

    @PostMapping(value ="/data")
    @Operation(summary = "接收音频数据", description = """
            用户停止输入后2秒，发送请求
            当用户连续输入超过30秒后，强制发送请求
            """)
    public Result<?> audioData(@RequestBody byte[] audioData) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.debug("接收音频数据块，currentId: {}, 大小：{} bytes", currentId, audioData.length);
        // 存储音频数据到缓存区
        AudioCache.addAudioData(currentId, audioData);
        return Result.success();
    }

    @PostMapping(value = "/model", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(type = RateLimit.RateLimitType.INTERFACE, key = "emotion-audio", maxRequests = 1, windowSizeInMillis = 2000)
    @Operation(summary = "音频 AI 模型处理", description = """
            客户端发送完整音频数据后调用此接口，触发 AI 处理流程：ASR → 情感识别 → 诊断 → 回答 → TTS → 语音数据流式返回
            用户停止输入后3秒，发送请求
            当用户连续输入超过30秒后，强制发送请求（需在发送‘接收音频数据接口’后发送）
            """)
    public StreamingResponseBody audioModel(@RequestBody @Validated AudioModelDTO audioModelDTO) {
        log.info("音频模型调用: {}", audioModelDTO);
        // 直接返回流式响应体（Spring MVC 原生支持，无任何报错）
        return outputStream -> {
            try {
                audioService.audioModel(audioModelDTO, outputStream);
            } catch (Exception e) {
                log.error("流式输出异常", e);
            }
        };
    }

    @PostMapping("/stop")
    @Operation(summary = "结束音频功能", description = """
            客户端结束语音对话时调用此接口，清理音频缓存
            调用接口结束后，需要取消订阅WebSocket通道：/user/queue/audio/conversationId、/user/queue/audio/round
            """)
    public Result<ConversationVO> audioStop(@RequestParam(required = false) @Parameter(description = "会话ID", required = false) Long conversationId) {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("音频功能结束-用户ID：{}, 会话ID：{}", currentId, conversationId);
        ConversationVO result = audioService.audioStop(conversationId);
        return Result.success(result);
    }

    @PostMapping("/interrupt")
    @Operation(summary = "中断音频流", description = "客户端需要打断 AI 回复时调用此接口")
    public Result<?> audioStreamInterrupt() {
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("中断音频流式响应：{}", currentId);
        // 中断 TTS 流式响应
        TtsStreamHolder.interrupt(currentId);
        return Result.success();
    }

    @PutMapping("/{conversationId}/initialize-name")
    @Operation(summary = "增添语音会话名称", description = "在语音对话结束后，前端需要调用接口，用于增添语音会话名称")
    public Result<String> initializeConversationName(
            @PathVariable @Parameter(description = "会话ID", required = true) @NotNull Long conversationId
    ) {
        log.info("增添语音会话名称:{}", conversationId);
        String result = audioService.initializeConversationName(conversationId);
        return Result.success(result);
    }
}
