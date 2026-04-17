package org.lixiyun.common.agent.prompt.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.prompt.service.PromptService;
import org.lixiyun.common.core.result.Result;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Prompt相关接口控制器
 * @author lixiyun
 * @since 2026-04-17 20:39
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/prompt")
@Tag(name = "Prompt相关接口", description = "Prompt相关接口")
public class PromptController {

    private final PromptService promptService;

    @GetMapping("/all-file-names")
    @Operation(summary = "查询所有文件名", description = "获取所有提示词文件夹名称")
    public Result<List<String>> getAllFileNames() {
        log.info("查询所有文件名");
        List<String> result = promptService.getAllFileNames();
        return Result.success(result);
    }

    @GetMapping("/file-content")
    @Operation(summary = "查询指定文件内容", description = "根据文件名查询指定文件内容")
    public Result<String> getFileContent(
            @RequestParam @Parameter(description = "文件名", required = true) @NotBlank String fileName
    ) {
        log.info("查询指定文件内容: {}", fileName);
        String result = promptService.getFileContent(fileName);
        return Result.success(result);
    }

    @PutMapping("/file-content")
    @Operation(summary = "修改指定文件内容", description = "根据文件名修改指定文件内容，替换Prompt_MAP中的value")
    public Result<?> updateFileContent(
            @RequestParam @Parameter(description = "文件名", required = true) @NotBlank String fileName,
            @RequestBody @Parameter(description = "文件内容", required = true) @NotBlank String content
    ) {
        log.info("修改指定文件内容: {}", fileName);
        promptService.updateFileContent(fileName, content);
        return Result.success();
    }

}
