package org.lixiyun.server.controller.chat;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.chat.ChatDTO;
import org.lixiyun.server.service.ChatService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author lixiyun
 * @since 2026-03-16 12:51
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
@Tag(name = "AI问答相关接口", description = "AI问答相关接口")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat")
    public Result<?> chat(@RequestBody @Validated ChatDTO chatDTO) throws GraphStateException {
        log.info("用户聊天：{}", chatDTO);
        chatService.chat(chatDTO);
        return Result.success();
    }

}
