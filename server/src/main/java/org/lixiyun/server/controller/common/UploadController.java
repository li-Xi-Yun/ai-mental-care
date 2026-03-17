package org.lixiyun.server.controller.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.server.service.UploadService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author lixiyun
 * @since 2025-12-28 20:51
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/upload")
@Tag(name = "上传接口", description = "上传接口")
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/image")
    @Operation(summary = "图片上传", description = "图片上传")
    public Result<String> imageUpload(@RequestPart @Schema(description = "图片文件") MultipartFile file) {
        log.info("图片文件上传:{}", file);
        String result = uploadService.imageUpload(file);
        return Result.success(result);
    }

}