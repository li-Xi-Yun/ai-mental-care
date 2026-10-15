package org.lixiyun.server.controller.admin.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.config.Live2dConfigDTO;
import org.lixiyun.pojo.dto.admin.config.Live2dConfigQueryDTO;
import org.lixiyun.pojo.vo.admin.config.Live2dConfigAdminVO;
import org.lixiyun.server.service.admin.AdminLive2dConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理员 Live2D 模型配置相关接口
 * <p>模型包上传（zip → 解析 → 落库）、配置维护、启用互斥切换与分页查询</p>
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/live2d-model")
@Tag(name = "管理员Live2D模型配置接口", description = "管理员Live2D模型配置接口")
public class AdminLive2dConfigController {

    private final AdminLive2dConfigService adminLive2dConfigService;

    @PostMapping
    @PreAuthorize("hasAuthority('live2d:config:create')")
    @Operation(summary = "上传Live2D模型包", description = "上传 zip 模型包：解压到模型根目录 → 一次遍历解析 exp3/motion3 提取白名单 → 落库（enabled=0；coverImagePath/semanticPrompt 未传为 null，需手动补录）")
    public Result<Long> uploadModel(
            @RequestParam("file") @Parameter(description = "Live2D模型包 zip 文件", required = true) MultipartFile file,
            @RequestParam("modelName") @Parameter(description = "模型名称（即解压目录名，仅支持字母/数字/下划线/中划线）", required = true) String modelName,
            @RequestParam(value = "expressionFileName", required = false) @Parameter(description = "表情清单文件名（可选，缺省按默认后缀 *.exp3.json 模糊匹配）") String expressionFileName,
            @RequestParam(value = "motionFileName", required = false) @Parameter(description = "动作清单文件名（可选，缺省按默认后缀 *.motion3.json 模糊匹配）") String motionFileName,
            @RequestParam(value = "semanticPrompt", required = false) @Parameter(description = "语义提示词（可选，缺省为 null 由管理员手动录入）") String semanticPrompt,
            @RequestParam(value = "sort", required = false) @Parameter(description = "排序值（可选，缺省 0）") Integer sort,
            @RequestParam(value = "remark", required = false) @Parameter(description = "备注（可选）") String remark) {
        log.info("上传Live2D模型包，modelName：{}，expressionFileName：{}，motionFileName：{}，原始文件名：{}",
                modelName, expressionFileName, motionFileName, file.getOriginalFilename());
        Long id = adminLive2dConfigService.uploadModel(file, modelName, expressionFileName, motionFileName,
                semanticPrompt, sort, remark);
        return Result.success(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('live2d:config:update')")
    @Operation(summary = "修改Live2D模型配置", description = "更新语义提示词、白名单、封面图等配置字段")
    public Result<Void> update(
            @PathVariable @NotNull @Parameter(description = "模型配置ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestBody @Validated(UpdateGroup.class) Live2dConfigDTO dto) {
        log.info("修改Live2D模型配置，id：{}，dto：{}", id, dto);
        adminLive2dConfigService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('live2d:config:delete')")
    @Operation(summary = "删除Live2D模型配置", description = "根据主键ID逻辑删除配置（deleted=1）")
    public Result<Void> delete(
            @PathVariable @NotNull @Parameter(description = "模型配置ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("删除Live2D模型配置，id：{}", id);
        adminLive2dConfigService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('live2d:config:update')")
    @Operation(summary = "启用/切换Live2D模型", description = "置 1 时事务内全量置 0 后置当前 1，保证全局唯一启用；置 0 为禁用")
    public Result<Void> enable(
            @PathVariable @NotNull @Parameter(description = "模型配置ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestParam @NotNull @NumberOfRanges(min = 0, max = 1) @Parameter(description = "是否启用：0-禁用 1-启用", required = true) Integer enabled) {
        log.info("启用/切换Live2D模型，id：{}，enabled：{}", id, enabled);
        adminLive2dConfigService.enable(id, enabled);
        return Result.success();
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('live2d:config:list')")
    @Operation(summary = "分页查询Live2D模型配置", description = "支持模型名称模糊匹配、启用状态筛选")
    public Result<PageResult<Live2dConfigAdminVO>> page(@RequestBody @Validated Live2dConfigQueryDTO queryDTO) {
        log.info("分页查询Live2D模型配置：{}", queryDTO);
        PageResult<Live2dConfigAdminVO> result = adminLive2dConfigService.page(queryDTO);
        return Result.success(result);
    }
}