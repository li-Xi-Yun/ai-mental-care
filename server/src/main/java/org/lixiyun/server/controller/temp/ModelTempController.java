package org.lixiyun.server.controller.temp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lixiyun
 * @date 2026/9/9 13:15
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/temp/model")
@Tag(name = "模型测试接口", description = "Ollama模型同步调用测试接口")
public class ModelTempController {

    @Qualifier("ollamaChatModel")
    private final ChatModel ollamaChatModel;

    @PostMapping("/chat")
    @Operation(summary = "Ollama同步对话", description = "接收前端文本，调用Ollama模型进行同步响应")
    public Result<String> chat(@RequestParam @Parameter(description = "用户输入的文本") String userMessage) {
        log.info("Ollama同步对话请求：{}", userMessage);
        String response = ollamaChatModel.call(new Prompt(userMessage))
                .getResult()
                .getOutput()
                .getText();
        log.info("Ollama同步对话响应：{}", response);
        return Result.success(response);
    }

}