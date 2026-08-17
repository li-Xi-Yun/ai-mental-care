package org.lixiyun.server.controller.user.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.aop.annotation.RateLimit;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.conversation.UserMessageSendDTO;
import org.lixiyun.pojo.vo.user.conversation.UserMessageSendVO;
import org.lixiyun.server.service.user.AIChatService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI聊天控制器
 * <p>提供用户消息发送等AI聊天相关接口</p>
 *
 * @author lixiyun
 * @since 2026-07-14 16:27
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/conversation/ai-chat")
@Tag(name = "AI聊天接口", description = "AI聊天相关接口")
public class AIController {

    private final AIChatService aiChatService;

    @PostMapping("/user-message")
    @Operation(summary = "发送用户消息", description = """
            用户发送消息接口，支持新会话创建和已有会话消息发送
            - 如果不传会话ID，系统会自动创建新会话并返回会话ID和当前轮次（为1）
            - 如果传入会话ID，系统会验证会话有效性并返回当前轮次（在上次轮次基础上加一）
            - 返回的会话ID和轮次信息用于后续对话和前端状态管理
            """)
    @RateLimit(type = RateLimit.RateLimitType.INTERFACE, key = "user-text", maxRequests = 1, windowSizeInMillis = 2000)
    public Result<UserMessageSendVO> sendUserMessage(@RequestBody @Validated UserMessageSendDTO userMessageSendDTO) {
        log.info("接收用户消息发送请求：{}", userMessageSendDTO);
        UserMessageSendVO result = aiChatService.sendUserMessage(userMessageSendDTO);
        return Result.success(result);
    }

}