package org.lixiyun.server.controller.conversation;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.aop.annotation.RateLimit;
import org.lixiyun.pojo.dto.chat.ChatDTO;
import org.lixiyun.server.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author lixiyun
 * @since 2026-03-16 12:51
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/conversaion/ai-chat")
@Tag(name = "AI问答相关接口", description = "AI问答相关接口")
public class ChatController {

    private final ChatService chatService;

    @PostMapping(value = "/emotion-analysis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "情绪分析接口", description = "模型接收分析用户每次输入时的情绪内容并进行回复")
    @RateLimit(type = RateLimit.RateLimitType.USER, key = "emotion-chat", maxRequests = 1, windowSizeInMillis = 1000)
    public Flux<String> chat(@RequestBody @Validated ChatDTO chatDTO) throws GraphStateException {
        log.info("用户聊天：{}", chatDTO);
        Flux<String> result = chatService.chat(chatDTO);
        return result;
    }

}
