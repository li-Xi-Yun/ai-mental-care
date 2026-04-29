package org.lixiyun.common.agent.prompt.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.prompt.dto.FolderContentItem;
import org.lixiyun.common.agent.prompt.service.PromptService;
import org.lixiyun.common.core.result.Result;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/file-content")
    @PreAuthorize("hasAuthority('prompt:file:content:select')")
    @Operation(summary = "查询指定文件内容", description = "根据文件夹路径和文件名查询指定文件内容")
    public Result<String> getFileContent(
            @RequestParam @Parameter(description = "文件对应的文件夹路径（完整）", required = true) @NotBlank String folderUrl,
            @RequestParam @Parameter(description = "文件名", required = true) @NotBlank String fileName
    ) {
        log.info("查询指定文件内容, 文件夹路径: {}, 文件名: {}", folderUrl, fileName);
        String result = promptService.getFileContent(folderUrl, fileName);
        return Result.success(result);
    }

    @PutMapping("/file-content")
    @PreAuthorize("hasAuthority('prompt:file:content:update')")
    @Operation(summary = "修改指定文件内容", description = "根据文件夹路径和文件名修改指定文件内容，替换Prompt_MAP中的value")
    public Result<?> updateFileContent(
            @RequestParam @Parameter(description = "文件对应的文件夹路径（完整）", required = true) @NotBlank String folderUrl,
            @RequestParam @Parameter(description = "文件名", required = true) @NotBlank String fileName,
            @RequestParam @Parameter(description = "文件内容", required = true) @NotBlank String content
    ) {
        log.info("修改指定文件内容, 文件夹路径: {}, 文件名: {}, 内容长度: {}", folderUrl, fileName, content.length());
        promptService.updateFileContent(folderUrl, fileName, content);
        return Result.success();
    }

    @GetMapping("/folder-contents")
    @PreAuthorize("hasAuthority('prompt:folder:name:select')")
    @Operation(summary = "查询指定文件夹下的所有内容", description = "根据文件夹路径查询该文件夹下的所有文件和子文件夹名称")
    public Result<List<FolderContentItem>> getFolderContents(
            @RequestParam(required = false) @Parameter(description = "文件夹路径（支持多级目录，根目录时不用传）", required = false) String folderUrl
    ) {
        log.info("查询指定文件夹下的所有内容, 文件夹路径: {}", folderUrl);
        List<FolderContentItem> result = promptService.getFolderContents(folderUrl);
        return Result.success(result);
    }

}
