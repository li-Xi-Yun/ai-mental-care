package org.lixiyun.server.controller.conversation;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.aop.annotation.RateLimit;
import org.lixiyun.pojo.dto.chat.ChatDTO;
import org.lixiyun.server.service.TextService;
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
public class TextController {

    private final TextService textService;

    @PostMapping(value = "/emotion-analysis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "情绪分析接口", description = """
            模型接收分析用户每次输入时的情绪内容并进行回复
            如果是模型思考的结果，消息前面会有：'模型思考：'前缀，前端需要将每一个前缀进行删除，保留后面的信息，进行流式展示
            如果是第一次对话，那在结果输出结束前的最后一条数据会有一个前缀：‘会话ID：’，后面是会话ID的数值，前端需要获取并设置
    """)
    @RateLimit(type = RateLimit.RateLimitType.USER, key = "emotion-chat", maxRequests = 1, windowSizeInMillis = 1000)
    public Flux<String> chat(@RequestBody @Validated ChatDTO chatDTO) throws GraphStateException {
        log.info("用户聊天：{}", chatDTO);
        Flux<String> result = textService.chat(chatDTO);
        return result;
    }

}
