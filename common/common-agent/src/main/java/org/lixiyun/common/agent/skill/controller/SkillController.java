package org.lixiyun.common.agent.skill.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.skill.pojo.vo.SkillContentItemVO;
import org.lixiyun.common.agent.skill.service.SkillService;
import org.lixiyun.common.core.result.Result;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Skill相关接口控制器
 *
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

    @GetMapping("/folder-contents")
    @PreAuthorize("hasAuthority('skill:folder:list')")
    @Operation(summary = "查询文件夹内容", description = "根据Skill文件夹路径查询该文件夹下的所有文件和子文件夹，支持传入补充文件夹路径进行拼接查询")
    public Result<List<SkillContentItemVO>> queryFolderContents(
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径（支持多级目录）", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl) {
        log.info("查询Skill文件夹内容, 文件夹路径: {}, 补充路径: {}", skillFolderUrl, supplementaryFolderUrl);
        List<SkillContentItemVO> result = skillService.queryFolderContents(skillFolderUrl, supplementaryFolderUrl);
        return Result.success(result);
    }

    @GetMapping("/file-content")
    @PreAuthorize("hasAuthority('skill:file:content:select')")
    @Operation(summary = "查询文件内容", description = "根据文件类型智能返回：文本文件返回文本内容（JSON），非文本文件（图片/PDF/Word等）返回文件数据流")
    public ResponseEntity<?> queryFileContent(
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl,
            @RequestParam @NotBlank(message = "文件名不能为空")
            @Parameter(description = "文件名", required = true) String fileName) {
        log.info("查询Skill文件内容, 文件夹路径: {}, 补充路径: {}, 文件名: {}", skillFolderUrl, supplementaryFolderUrl, fileName);
        return skillService.queryFileContent(skillFolderUrl, supplementaryFolderUrl, fileName);
    }

    @PostMapping("/folder")
    @PreAuthorize("hasAuthority('skill:folder:create')")
    @Operation(summary = "新建文件夹", description = "在指定的Skill文件夹路径下创建新文件夹")
    public Result<Void> createFolder(
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl,
            @RequestParam @NotBlank(message = "文件夹名称不能为空")
            @Parameter(description = "文件夹名称", required = true) String folderName) {
        log.info("新建Skill文件夹, 路径: {}, 补充路径: {}, 名称: {}", skillFolderUrl, supplementaryFolderUrl, folderName);
        skillService.createFolder(skillFolderUrl, supplementaryFolderUrl, folderName);
        return Result.success();
    }

    @PostMapping("/file")
    @PreAuthorize("hasAuthority('skill:file:create')")
    @Operation(summary = "新建文件", description = "在指定的Skill文件夹路径下创建新文件")
    public Result<Void> createFile(
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl,
            @RequestParam @NotBlank(message = "文件名不能为空")
            @Parameter(description = "文件名", required = true) String fileName) {
        log.info("新建Skill文件, 路径: {}, 补充路径: {}, 名称: {}", skillFolderUrl, supplementaryFolderUrl, fileName);
        skillService.createFile(skillFolderUrl, supplementaryFolderUrl, fileName);
        return Result.success();
    }

    @DeleteMapping("/folder")
    @PreAuthorize("hasAuthority('skill:folder:delete')")
    @Operation(summary = "删除文件夹", description = "删除指定的Skill文件夹及其所有子内容和子文件夹，SKILL根目录不允许删除")
    public Result<Void> deleteFolder(
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl) {
        log.info("删除Skill文件夹, 路径: {}, 补充路径: {}", skillFolderUrl, supplementaryFolderUrl);
        skillService.deleteFolder(skillFolderUrl, supplementaryFolderUrl);
        return Result.success();
    }

    @DeleteMapping("/file")
    @PreAuthorize("hasAuthority('skill:file:delete')")
    @Operation(summary = "删除文件", description = "删除指定的Skill文件，如果是SKILL.md文件则需要更新影子表中的value值")
    public Result<Void> deleteFile(
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl,
            @RequestParam @NotBlank(message = "文件名不能为空")
            @Parameter(description = "文件名", required = true) String fileName) {
        log.info("删除Skill文件, 路径: {}, 补充路径: {}, 文件名: {}", skillFolderUrl, supplementaryFolderUrl, fileName);
        skillService.deleteFile(skillFolderUrl, supplementaryFolderUrl, fileName);
        return Result.success();
    }

    @PutMapping("/file/rename")
    @PreAuthorize("hasAuthority('skill:file:rename')")
    @Operation(summary = "修改文件名", description = "修改指定文件的名称，如果是SKILL.md文件需要特殊处理并更新影子表数据")
    public Result<Void> renameFile(
            @RequestParam @NotBlank(message = "新文件名不能为空")
            @Parameter(description = "新文件名", required = true) String newFileName,
            @RequestParam @NotBlank(message = "原文件名不能为空")
            @Parameter(description = "原文件名", required = true) String originalFileName,
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl) {
        log.info("修改Skill文件名, 新文件名: {}, 原文件名: {}, 路径: {}, 补充路径: {}", newFileName, originalFileName, skillFolderUrl, supplementaryFolderUrl);
        skillService.renameFile(newFileName, originalFileName, skillFolderUrl, supplementaryFolderUrl);
        return Result.success();
    }

    @PostMapping("/file/upload")
    @PreAuthorize("hasAuthority('skill:file:upload')")
    @Operation(summary = "上传单个文件", description = "上传单个文件到指定的Skill文件夹路径下，支持大小校验和同名文件检查")
    public Result<Void> uploadFile(
            @RequestParam
            @Parameter(description = "文件数据", required = true) MultipartFile file,
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl) {
        log.info("上传Skill文件, 文件名: {}, 路径: {}, 补充路径: {}", file != null ? file.getOriginalFilename() : null, skillFolderUrl, supplementaryFolderUrl);
        skillService.uploadFile(file, skillFolderUrl, supplementaryFolderUrl);
        return Result.success();
    }

    @PostMapping("/folder/upload")
    @PreAuthorize("hasAuthority('skill:folder:upload')")
    @Operation(summary = "上传压缩包为文件夹", description = "上传zip压缩包到指定路径并解压，自动更新影子表数据和节点列表信息")
    public Result<Void> uploadFolder(
            @RequestParam
            @Parameter(description = "压缩文件数据（zip格式）", required = true) MultipartFile zipFile,
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl) {
        log.info("上传Skill文件夹(压缩包), 压缩包名: {}, 路径: {}, 补充路径: {}", zipFile != null ? zipFile.getOriginalFilename() : null, skillFolderUrl, supplementaryFolderUrl);
        skillService.uploadFolder(zipFile, skillFolderUrl, supplementaryFolderUrl);
        return Result.success();
    }

    @PutMapping("/file/content")
    @PreAuthorize("hasAuthority('skill:file:content:update')")
    @Operation(summary = "修改文件内容", description = "修改指定文件的内容，如果是SKILL.md文件需要解析元数据格式并更新影子表")
    public Result<Void> updateFileContent(
            @RequestParam @NotBlank(message = "新文件内容不能为空")
            @Parameter(description = "新文件内容", required = true) String newContent,
            @RequestParam @NotBlank(message = "文件名不能为空")
            @Parameter(description = "文件名", required = true) String fileName,
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl) {
        log.info("修改Skill文件内容, 文件名: {}, 路径: {}, 补充路径: {}", fileName, skillFolderUrl, supplementaryFolderUrl);
        skillService.updateFileContent(newContent, fileName, skillFolderUrl, supplementaryFolderUrl);
        return Result.success();
    }

    @PutMapping("/folder/rename")
    @PreAuthorize("hasAuthority('skill:folder:rename')")
    @Operation(summary = "修改文件夹名称", description = "重命名指定的Skill文件夹，同时更新影子表中相关的路径信息和物理文件夹名称")
    public Result<Void> renameFolder(
            @RequestParam @NotBlank(message = "新文件夹名称不能为空")
            @Parameter(description = "新文件夹名称", required = true) String newFolderName,
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl) {
        log.info("修改Skill文件夹名称, 新名称: {}, 路径: {}, 补充路径: {}", newFolderName, skillFolderUrl, supplementaryFolderUrl);
        skillService.renameFolder(newFolderName, skillFolderUrl, supplementaryFolderUrl);
        return Result.success();
    }

    @GetMapping("/folder/download")
    @PreAuthorize("hasAuthority('skill:folder:download')")
    @Operation(summary = "下载文件夹为ZIP压缩包", description = "将指定的Skill文件夹打包成ZIP压缩包并流式返回，支持传入补充文件夹路径进行拼接查询")
    public ResponseEntity<Resource> downloadFolderAsZip(
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl) {
        log.info("下载Skill文件夹(ZIP), 文件夹路径: {}, 补充路径: {}", skillFolderUrl, supplementaryFolderUrl);
        return skillService.downloadFolderAsZip(skillFolderUrl, supplementaryFolderUrl);
    }

    @GetMapping("/file/download")
    @PreAuthorize("hasAuthority('skill:file:download')")
    @Operation(summary = "下载单个文件", description = "下载指定的Skill文件，支持传入补充文件夹路径进行拼接，流式返回文件数据")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam @NotBlank(message = "Skill文件夹路径不能为空")
            @Parameter(description = "Skill文件夹路径", required = true) String skillFolderUrl,
            @RequestParam
            @Parameter(description = "补充文件夹路径（可选）", required = false) String supplementaryFolderUrl,
            @RequestParam @NotBlank(message = "文件名不能为空")
            @Parameter(description = "文件名", required = true) String fileName) {
        log.info("下载Skill文件, 文件夹路径: {}, 补充路径: {}, 文件名: {}", skillFolderUrl, supplementaryFolderUrl, fileName);
        return skillService.downloadFile(skillFolderUrl, supplementaryFolderUrl, fileName);
    }
}
