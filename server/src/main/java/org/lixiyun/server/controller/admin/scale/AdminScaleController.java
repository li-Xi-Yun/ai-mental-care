package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQueryDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleStatusDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleDetailVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleVO;
import org.lixiyun.server.service.admin.AdminScaleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员量表主表相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale")
@Tag(name = "管理员量表主表相关接口", description = "管理员量表主表相关接口")
public class AdminScaleController {

    private final AdminScaleService adminScaleService;

    @PostMapping
    @PreAuthorize("hasAuthority('scale:scale:create')")
    @Operation(summary = "新增量表主表", description = "新增量表主表，可同时创建并发布首个版本")
    public Result<Void> createScale(@RequestBody @Validated(AddGroup.class) AdminScaleDTO dto) {
        log.info("新增量表主表：{}", dto);
        adminScaleService.createScale(dto);
        return Result.success();
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('scale:scale:list')")
    @Operation(summary = "分页查询量表主表", description = "支持名称模糊匹配、分类筛选、状态与删除状态筛选")
    public Result<PageResult<AdminScaleVO>> pageScales(@RequestBody @Validated AdminScaleQueryDTO queryDTO) {
        log.info("分页查询量表主表：{}", queryDTO);
        PageResult<AdminScaleVO> result = adminScaleService.pageScales(queryDTO);
        return Result.success(result);
    }

    @GetMapping("/{scaleId}")
    @PreAuthorize("hasAuthority('scale:scale:list')")
    @Operation(summary = "获取量表详情", description = "获取量表详情，含当前版本摘要与全部版本列表")
    public Result<AdminScaleDetailVO> getScaleDetail(
            @PathVariable @NotNull(message = "量表ID不能为空") @Parameter(description = "量表ID", required = true, in = ParameterIn.PATH) Long scaleId) {
        log.info("获取量表详情，scaleId：{}", scaleId);
        return Result.success(adminScaleService.getScaleDetail(scaleId));
    }

    @PutMapping("/{scaleId}")
    @PreAuthorize("hasAuthority('scale:scale:update')")
    @Operation(summary = "修改量表主表", description = "修改量表基本元数据")
    public Result<Void> updateScale(
            @PathVariable @NotNull(message = "量表ID不能为空") @Parameter(description = "量表ID", required = true, in = ParameterIn.PATH) Long scaleId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleDTO dto) {
        log.info("修改量表主表，scaleId：{}，dto：{}", scaleId, dto);
        adminScaleService.updateScale(scaleId, dto);
        return Result.success();
    }

    @PutMapping("/{scaleId}/status")
    @PreAuthorize("hasAuthority('scale:scale:update')")
    @Operation(summary = "启用/禁用量表", description = "只更新状态字段，0=禁用，1=启用")
    public Result<Void> updateScaleStatus(
            @PathVariable @NotNull(message = "量表ID不能为空") @Parameter(description = "量表ID", required = true, in = ParameterIn.PATH) Long scaleId,
            @RequestBody @Validated AdminScaleStatusDTO dto) {
        log.info("启用/禁用量表，scaleId：{}，status：{}", scaleId, dto.getStatus());
        adminScaleService.updateScaleStatus(scaleId, dto);
        return Result.success();
    }

    @DeleteMapping("/{scaleId}")
    @PreAuthorize("hasAuthority('scale:scale:delete')")
    @Operation(summary = "删除量表主表", description = "级联逻辑删除量表及其版本与内容，有测评记录时仅提示")
    public Result<Void> deleteScale(
            @PathVariable @NotNull(message = "量表ID不能为空") @Parameter(description = "量表ID", required = true, in = ParameterIn.PATH) Long scaleId) {
        log.info("删除量表主表，scaleId：{}", scaleId);
        adminScaleService.deleteScale(scaleId);
        return Result.success();
    }

    @PutMapping("/{scaleId}/restore")
    @PreAuthorize("hasAuthority('scale:scale:update')")
    @Operation(summary = "恢复量表主表", description = "级联恢复已删除的量表及其版本与内容")
    public Result<Void> restoreScale(
            @PathVariable @NotNull(message = "量表ID不能为空") @Parameter(description = "量表ID", required = true, in = ParameterIn.PATH) Long scaleId) {
        log.info("恢复量表主表，scaleId：{}", scaleId);
        adminScaleService.restoreScale(scaleId);
        return Result.success();
    }

}