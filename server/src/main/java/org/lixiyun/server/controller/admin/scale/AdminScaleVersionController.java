package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleVersionCopyDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleVersionDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleVersionVO;
import org.lixiyun.server.service.admin.AdminScaleVersionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员量表版本相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/version")
@Tag(name = "管理员量表版本相关接口", description = "管理员量表版本相关接口")
public class AdminScaleVersionController {

    private final AdminScaleVersionService adminScaleVersionService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('scale:version:list')")
    @Operation(summary = "查询版本列表", description = "查询指定量表下的版本列表，含题目/维度统计与是否当前生效")
    public Result<List<AdminScaleVersionVO>> listVersions(
            @RequestParam @NotNull(message = "量表ID不能为空") @Parameter(description = "量表ID", required = true, in = ParameterIn.QUERY) Long scaleId) {
        log.info("查询版本列表，scaleId：{}", scaleId);
        return Result.success(adminScaleVersionService.listVersions(scaleId));
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasAuthority('scale:version:list')")
    @Operation(summary = "获取版本详情", description = "根据版本ID获取版本详细信息")
    public Result<AdminScaleVersionVO> getVersionDetail(
            @PathVariable @NotNull(message = "版本ID不能为空") @Parameter(description = "版本ID", required = true, in = ParameterIn.PATH) Long versionId) {
        log.info("获取版本详情，versionId：{}", versionId);
        return Result.success(adminScaleVersionService.getVersionDetail(versionId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('scale:version:create')")
    @Operation(summary = "新增空版本", description = "为量表新增一个空版本")
    public Result<Void> createVersion(@RequestBody @Validated(AddGroup.class) AdminScaleVersionDTO dto) {
        log.info("新增空版本：{}", dto);
        adminScaleVersionService.createVersion(dto);
        return Result.success();
    }

    @PostMapping("/copy")
    @PreAuthorize("hasAuthority('scale:version:create')")
    @Operation(summary = "复制版本", description = "从源版本整卷复制出新的版本（维度/题目/选项/规则/常模）")
    public Result<Void> copyVersion(@RequestBody @Validated AdminScaleVersionCopyDTO dto) {
        log.info("复制版本：{}", dto);
        adminScaleVersionService.copyVersion(dto);
        return Result.success();
    }

    @PutMapping("/{versionId}")
    @PreAuthorize("hasAuthority('scale:version:update')")
    @Operation(summary = "修改版本信息", description = "修改版本的说明、版权信息与版本号")
    public Result<Void> updateVersion(
            @PathVariable @NotNull(message = "版本ID不能为空") @Parameter(description = "版本ID", required = true, in = ParameterIn.PATH) Long versionId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleVersionDTO dto) {
        log.info("修改版本信息，versionId：{}，dto：{}", versionId, dto);
        adminScaleVersionService.updateVersion(versionId, dto);
        return Result.success();
    }

    @PutMapping("/{versionId}/publish")
    @PreAuthorize("hasAuthority('scale:version:publish')")
    @Operation(summary = "发布版本", description = "发布版本为当前生效版本，写入量表 current_version_id")
    public Result<Void> publishVersion(
            @PathVariable @NotNull(message = "版本ID不能为空") @Parameter(description = "版本ID", required = true, in = ParameterIn.PATH) Long versionId) {
        log.info("发布版本，versionId：{}", versionId);
        adminScaleVersionService.publishVersion(versionId);
        return Result.success();
    }

    @DeleteMapping("/{versionId}")
    @PreAuthorize("hasAuthority('scale:version:delete')")
    @Operation(summary = "删除版本", description = "当前生效版本禁止删除，需先发布其他版本")
    public Result<Void> deleteVersion(
            @PathVariable @NotNull(message = "版本ID不能为空") @Parameter(description = "版本ID", required = true, in = ParameterIn.PATH) Long versionId) {
        log.info("删除版本，versionId：{}", versionId);
        adminScaleVersionService.deleteVersion(versionId);
        return Result.success();
    }

    @PutMapping("/{versionId}/restore")
    @PreAuthorize("hasAuthority('scale:version:update')")
    @Operation(summary = "恢复版本", description = "恢复已删除的版本")
    public Result<Void> restoreVersion(
            @PathVariable @NotNull(message = "版本ID不能为空") @Parameter(description = "版本ID", required = true, in = ParameterIn.PATH) Long versionId) {
        log.info("恢复版本，versionId：{}", versionId);
        adminScaleVersionService.restoreVersion(versionId);
        return Result.success();
    }

}