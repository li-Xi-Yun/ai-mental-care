package org.lixiyun.server.controller.user.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.conversation.AudioInterruptDTO;
import org.lixiyun.pojo.dto.user.conversation.AudioMessageSendDTO;
import org.lixiyun.pojo.vo.user.conversation.AudioSessionInitVO;
import org.lixiyun.server.service.user.AudioService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 语音对话控制器
 * <p>
 * 提供语音会话初始化、语音消息发送、语音中断、语音会话结束等功能。
 * 其中语音消息发送与语音中断使用WebSocket（STOMP）方式通信，
 * 其他两个为普通HTTP API接口。
 * </p>
 *
 * @author lixiyun
 * @since 2026-03-29 18:17
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/conversation/audio")
@Tag(name = "语音对话相关接口", description = "语音会话初始化、消息发送、中断和结束等接口")
public class AudioController {

    private final AudioService audioService;

    @PostMapping("/init")
    @Operation(summary = "初始化语音会话", description = "判断会话ID是否存在，不存在则创建新会话，返回会话信息和WebSocket连接地址；存在则检查状态是否正常")
    public Result<AudioSessionInitVO> initSession(
            @RequestParam(required = false)
            @Parameter(description = "会话ID（可选），第一次对话时不需要传递", required = false) Long conversationId
    ) {
        log.info("初始化语音会话, conversationId: {}", conversationId);
        AudioSessionInitVO result = audioService.initSession(conversationId);
        return Result.success(result);
    }

    @MessageMapping("/message/send")
    @Operation(summary = "发送语音消息（WebSocket）", description = "通过WebSocket发送用户语音消息，进行ASR语音转文本，实时展示处理结果")
    public void sendAudioMessage(
            @Payload @Valid AudioMessageSendDTO audioMessageSendDTO
    ) {
        log.info("发送语音消息, conversationId: {}", audioMessageSendDTO.getConversationId());
        audioService.sendAudioMessage(audioMessageSendDTO);
    }

    @MessageMapping("/interrupt")
    @Operation(summary = "中断语音对话（WebSocket）", description = "通过WebSocket中断当前正在进行的语音对话处理")
    public void interruptAudio(
            @Payload @Valid AudioInterruptDTO audioInterruptDTO
    ) {
        log.info("中断语音对话, conversationId: {}", audioInterruptDTO.getConversationId());
        audioService.interruptAudio(audioInterruptDTO);
    }

    @DeleteMapping("/{conversationId}/end")
    @Operation(summary = "结束语音会话", description = "结束指定会话的语音对话模式，切换回文本对话模式，清理相关资源")
    public Result<Void> endSession(
            @PathVariable @Parameter(description = "会话ID", required = true) @NotNull Long conversationId
    ) {
        log.info("结束语音会话, conversationId: {}", conversationId);
        audioService.endSession(conversationId);
        return Result.success();
    }

}