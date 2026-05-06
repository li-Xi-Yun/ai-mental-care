package org.lixiyun.server.controller.admin.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.file.FileQueryDTO;
import org.lixiyun.pojo.dto.admin.file.FileUpdateDTO;
import org.lixiyun.pojo.vo.admin.file.FileVO;
import org.lixiyun.server.service.admin.AdminFileService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件管理相关接口
 *
 * @author lixiyun
 * @since 2026-05-01 16:00
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/file")
@Tag(name = "文件相关接口", description = "文件相关接口")
public class AdminFileController {

    private final AdminFileService adminFileService;

    @PostMapping("/upload")
    @Operation(summary = "文件上传", description = "上传文件到本地存储，并保存文件元数据信息")
    public Result<FileVO> uploadFile(
            @RequestPart @Parameter(description = "文件数据", required = true) MultipartFile file,
            @RequestParam(required = false) @Parameter(description = "分类ID（可选），没有为默认分类") Long categoryId) {
        log.info("文件上传请求接口，文件大小：{}字节，分类ID：{}", file.getSize(), categoryId);
        FileVO fileVO = adminFileService.uploadFile(file, categoryId);
        return Result.success(fileVO);
    }

    @PostMapping("/page")
    @Operation(summary = "文件分页查询", description = "根据分类ID、文件名模糊查询、文件状态等条件分页查询文件")
    public Result<PageResult<FileVO>> queryFilePage(@RequestBody @Validated FileQueryDTO queryDTO) {
        log.info("文件分页查询请求接口：{}", queryDTO);
        PageResult<FileVO> pageResult = adminFileService.queryFilePage(queryDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "文件删除", description = "根据文件ID删除文件元数据信息")
    public Result<Void> deleteFile(
            @PathVariable @NotNull @Parameter(description = "文件ID", required = true, in = ParameterIn.PATH) Long fileId) {
        log.info("文件删除请求接口，文件ID：{}", fileId);
        adminFileService.deleteFile(fileId);
        return Result.success();
    }

    @PutMapping("/{fileId}")
    @Operation(summary = "文件元数据信息修改", description = "修改文件的文件名和分类ID等信息")
    public Result<FileVO> updateFileInfo(
            @PathVariable @NotNull @Parameter(description = "文件ID", required = true, in = ParameterIn.PATH) Long fileId,
            @RequestBody @Validated FileUpdateDTO updateDTO) {
        log.info("文件信息修改请求接口，文件ID：{}，修改内容：{}", fileId, updateDTO);
        // 将路径参数fileId设置到DTO中
        updateDTO.setId(fileId);
        FileVO fileVO = adminFileService.updateFileInfo(updateDTO);
        return Result.success(fileVO);
    }

}
