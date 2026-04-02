package org.lixiyun.server.controller.socket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.conversation.AudioModelDTO;
import org.lixiyun.server.infrastructure.audio.AudioCache;
import org.lixiyun.server.infrastructure.audio.TtsStreamHolder;
import org.lixiyun.server.service.AudioService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;

/**
 * 基于 STOMP 协议的语音对话 WebSocket 控制器
 * 
 * @author lixiyun
 * @since 2026-03-29 18:12
 */
@Slf4j
@Validated
@Controller
@RequiredArgsConstructor
@MessageMapping("/socket/audio")
@Tag(name = "语音对话相关接口（STOMP）", description = "基于 STOMP 协议的语音对话 WebSocket 接口")
public class AudioController1 {

    private final AudioService audioService;

    @MessageMapping("/start")
    @SendToUser(value = "/queue/audio/start", broadcast = false)
    @Operation(summary = "初始化音频数据缓存区", description = "客户端连接后首先调用此接口")
    public Result<?> audioStart(Principal principal) {
        UserInfoThreadLocalUtil.setSecurityContext(principal);
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("开始初始化音频数据缓存区（STOMP）：{}", currentId);
        AudioCache.initAudioCache(currentId);
//        if(!initFlat){
//            return Result.error(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
//        }
        return Result.success();
    }

    @MessageMapping("/data")
    @SendToUser(value = "/queue/audio/data", broadcast = false)
    @Operation(summary = "接收音频数据", description = "客户端分片发送音频数据时调用此接口")
    public Result<?> audioData(Principal principal, @Payload byte[] audioData) {
        UserInfoThreadLocalUtil.setSecurityContext(principal);
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.debug("接收音频数据块（STOMP），currentId: {}, 大小：{} bytes", currentId, audioData.length);
        // 存储音频数据到缓存区
        AudioCache.addAudioData(currentId, audioData);
        return Result.success();
    }

    @MessageMapping("/model")
    @SendToUser(value = "/queue/audio/model", broadcast = false)
    @Operation(summary = "音频 AI 模型处理", description = "客户端发送完整音频数据后调用此接口，触发 AI 处理流程：ASR → 情感识别 → 诊断 → 回答 → TTS")
    public Result<?> audioModel(Principal principal, @Payload @Validated AudioModelDTO audioModelDTO) {
        UserInfoThreadLocalUtil.setSecurityContext(principal);
        log.info("音频模型调用（STOMP）, conversationId: {}, currentRound: {}",
                audioModelDTO.getConversationId(), audioModelDTO.getCurrentRound());
        return Result.success();
//        audioService.audioModel(audioModelDTO, );
    }

    @MessageMapping("/stop")
    @SendToUser(value = "/queue/audio/stop", broadcast = false)
    @Operation(summary = "结束音频功能", description = "客户端结束语音对话时调用此接口，清理音频缓存")
    public Result<?> audioStop(Principal principal) {
        UserInfoThreadLocalUtil.setSecurityContext(principal);
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("音频功能结束（STOMP）：{}", currentId);
        AudioCache.clearAudioCache(currentId);
        return Result.success();
    }

    @MessageMapping("/interrupt")
    @SendToUser(value = "/queue/audio/interrupt", broadcast = false)
    @Operation(summary = "中断音频流", description = "客户端需要打断 AI 回复时调用此接口")
    public Result<?> audioStreamInterrupt(Principal principal) {
        UserInfoThreadLocalUtil.setSecurityContext(principal);
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.info("中断音频流式响应（STOMP）：{}", currentId);
        // 中断 TTS 流式响应
        TtsStreamHolder.interrupt(currentId);
        return Result.success();
    }

}

