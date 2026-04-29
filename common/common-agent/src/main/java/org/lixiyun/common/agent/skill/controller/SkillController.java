package org.lixiyun.common.agent.skill.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.skill.service.SkillService;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 23:12
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/skill")
@Tag(name = "Skill相关接口", description = "Skill相关接口")
public class SkillController {

    private final SkillService skillService;

    @GetMapping("/all-folder-names")
    @PreAuthorize("hasAuthority('skill:folder:name:select')")
    @Operation(summary = "获取所有技能文件夹名称", description = "获取所有技能文件夹名称")
    public Result<List<String>> getAllFolderName(
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize
    ) {
        log.info("获取所有技能文件夹名称: {}, {}", pageNum, pageSize);
        List<String> result = skillService.getAllFolderName(pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/all-file-names")
    @PreAuthorize("hasAuthority('skill:file:name:select')")
    @Operation(summary = "获取文件夹下所有文件名称", description = "获取指定文件夹名称下所有文件名称")
    public Result<List<String>> getAllFileByFolderName(
            @RequestParam @Parameter(description = "文件夹名称", required = true) @NotBlank String folderName
    ) {
        log.info("获取文件夹下所有文件名称: {}", folderName);
        List<String> result = skillService.getAllFileByFolderName(folderName);
        return Result.success(result);
    }

    @GetMapping("/file-context")
    @PreAuthorize("hasAuthority('skill:file:content:select')")
    @Operation(summary = "查询指定文件内容", description = "根据文件夹名、文件名，查询指定文件内容")
    public Result<String> getFileContext(
            @RequestParam @Parameter(description = "文件夹名称", required = true) @NotBlank String folderName,
            @RequestParam @Parameter(description = "文件名", required = true) @NotBlank String fileName
    ) {
        log.info("查询指定文件内容: {}, {}", folderName, fileName);
        String result = skillService.getFileContext(folderName, fileName);
        return Result.success(result);
    }

    @PutMapping("folder-name")
    @PreAuthorize("hasAuthority('skill:folder:name:update')")
    @Operation(summary = "修改文件夹名", description = "根据新旧文件夹名称，修改文件夹名称")
    public Result<?> updateFolderName(
            @RequestParam @Parameter(description = "旧文件夹名称", required = true) @NotBlank String oldFolderName,
            @RequestParam @Parameter(description = "新文件夹名称", required = true) @NotBlank String newFolderName
    ) {
        log.info("修改文件夹名：{}, {}", oldFolderName, newFolderName);
        skillService.updateFolderName(oldFolderName, newFolderName);
        return Result.success();
    }

    @PutMapping("file-name")
    @PreAuthorize("hasAuthority('skill:file:name:update')")
    @Operation(summary = "修改文件名", description = "根据文件夹名、新旧文件名，修改文件名称")
    public Result<?> updateFileName(
            @RequestParam @Parameter(description = "文件夹名称", required = true) @NotBlank String folderName,
            @RequestParam @Parameter(description = "旧文件名", required = true) @NotBlank String oldFileName,
            @RequestParam @Parameter(description = "新文件名", required = true) @NotBlank String newFileName
    ) {
        log.info("修改文件名：{}, {}, {}", folderName, oldFileName, newFileName);
        skillService.updateFileName(folderName, oldFileName, newFileName);
        return Result.success();
    }

    @PutMapping("file-context")
    @PreAuthorize("hasAuthority('skill:file:content:update')")
    @Operation(summary = "修改文件内容", description = "根据文件夹名、文件名、文件字符串，修改文件内容")
    public Result<?> updateFileContext(
            @RequestParam @Parameter(description = "文件夹名称", required = true) @NotBlank String folderName,
            @RequestParam @Parameter(description = "文件名", required = true) @NotBlank String fileName,
            @RequestParam @Parameter(description = "文件内容", required = true) @NotBlank String fileContext
    ) {
        log.info("修改文件内容：{}, {}, {}", folderName, fileName, fileContext);
        skillService.updateFileContext(folderName, fileName, fileContext);
        return Result.success();
    }

    @DeleteMapping("/folder")
    @PreAuthorize("hasAuthority('skill:folder:delete')")
    @Operation(summary = "删除文件夹", description = "根据文件夹名称，级联删除文件夹下的所有文件")
    public Result<Void> deleteFolder(@RequestParam @Parameter(description = "文件夹名称", required = true) @NotBlank String folderName) {
        log.info("删除文件夹: {}", folderName);
        skillService.deleteFolder(folderName);
        return Result.success();
    }

    @DeleteMapping("/file")
    @PreAuthorize("hasAuthority('skill:file:delete')")
    @Operation(summary = "删除文件", description = "根据文件夹名、文件名, 删除指定文件")
    public Result<Void> deleteFile(
            @RequestParam @Parameter(description = "文件夹名称", required = true) @NotBlank String folderName,
            @RequestParam @Parameter(description = "文件名", required = true) @NotBlank String fileName
    ) {
        log.info("删除文件: {}, {}", folderName, fileName);
        skillService.deleteFile(folderName, fileName);
        return Result.success();
    }

    @PostMapping("/folder")
    @PreAuthorize("hasAuthority('skill:folder:add')")
    @Operation(summary = "文件夹新增", description = "根据文件夹名称，在本地创建新的文件夹")
    public Result<?> addFolder(@RequestParam @Parameter(description = "文件夹名称", required = true) @NotBlank String folderName) {
        log.info("文件夹新增：{}", folderName);
        skillService.addFolder(folderName);
        return Result.success();
    }

    @PostMapping("/file")
    @PreAuthorize("hasAuthority('skill:file:add')")
    @Operation(summary = "文件新增", description = "根据文件夹名、文件名、文件字符串，在指定文件夹下创建新的文件，文件内容为传入的字符串")
    public Result<?> addFile(
            @RequestParam @Parameter(description = "文件夹名称", required = true) @NotBlank String folderName,
            @RequestParam @Parameter(description = "文件名", required = true) @NotBlank String fileName,
            @RequestParam @Parameter(description = "文件内容", required = true) @NotBlank String fileContext
    ) {
        log.info("文件新增：{}, {}, {}", folderName, fileName, fileContext);
        skillService.addFile(folderName, fileName, fileContext);
        return Result.success();
    }

    @PostMapping("/upload/folder")
    @PreAuthorize("hasAuthority('skill:file:upload')")
    @Operation(summary = "文件上传", description = "根据文件夹名、文件数据，在指定文件夹下创建新的文件")
    public Result<?> uploadFile(
            @RequestParam @Parameter(description = "文件夹名称", required = true) @NotBlank String folderName,
            @RequestPart @Parameter(description = "文件数据", required = true) @NotNull MultipartFile file
    ) {
        log.info("文件上传：{}, {} 字节", folderName, file.getSize());
        skillService.uploadFile(folderName, file);
        return Result.success();
    }

    @PostMapping("/upload/file")
    @PreAuthorize("hasAuthority('skill:folder:upload')")
    @Operation(summary = "文件夹上传", description = "根剧压缩文件夹数据，解压到指定文件夹下")
    public Result<?> uploadFolder(@RequestPart @Parameter(description = "文件数据", required = true) @NotNull MultipartFile file) {
        log.info("文件夹上传：{} 字节", file.getSize());
        skillService.uploadFolder(file);
        return Result.success();
    }

}