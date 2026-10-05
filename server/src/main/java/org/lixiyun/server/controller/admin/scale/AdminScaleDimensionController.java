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
import org.lixiyun.pojo.dto.admin.scale.AdminScaleDimensionDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleDimensionVO;
import org.lixiyun.server.service.admin.AdminScaleDimensionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员量表维度相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/dimension")
@Tag(name = "管理员量表维度相关接口", description = "管理员量表维度相关接口")
public class AdminScaleDimensionController {

    private final AdminScaleDimensionService adminScaleDimensionService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('scale:dimension:list')")
    @Operation(summary = "查询维度列表", description = "查询指定量表版本下的维度列表，按排序值升序")
    public Result<List<AdminScaleDimensionVO>> listDimensions(
            @RequestParam @NotNull(message = "量表版本ID不能为空") @Parameter(description = "量表版本ID", required = true, in = ParameterIn.QUERY) Long scaleVersionId) {
        log.info("查询维度列表，scaleVersionId：{}", scaleVersionId);
        return Result.success(adminScaleDimensionService.listDimensions(scaleVersionId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('scale:dimension:create')")
    @Operation(summary = "新增维度", description = "为指定量表版本新增维度")
    public Result<Void> createDimension(@RequestBody @Validated(AddGroup.class) AdminScaleDimensionDTO dto) {
        log.info("新增维度：{}", dto);
        adminScaleDimensionService.createDimension(dto);
        return Result.success();
    }

    @PutMapping("/{dimensionId}")
    @PreAuthorize("hasAuthority('scale:dimension:update')")
    @Operation(summary = "修改维度", description = "修改维度名称、编码与说明")
    public Result<Void> updateDimension(
            @PathVariable @NotNull(message = "维度ID不能为空") @Parameter(description = "维度ID", required = true, in = ParameterIn.PATH) Long dimensionId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleDimensionDTO dto) {
        log.info("修改维度，dimensionId：{}，dto：{}", dimensionId, dto);
        adminScaleDimensionService.updateDimension(dimensionId, dto);
        return Result.success();
    }

    @PutMapping("/{dimensionId}/sort")
    @PreAuthorize("hasAuthority('scale:dimension:update')")
    @Operation(summary = "调整维度排序", description = "仅更新维度排序值")
    public Result<Void> updateDimensionSort(
            @PathVariable @NotNull(message = "维度ID不能为空") @Parameter(description = "维度ID", required = true, in = ParameterIn.PATH) Long dimensionId,
            @RequestParam @NotNull(message = "排序不能为空") @Parameter(description = "排序值", required = true, in = ParameterIn.QUERY) Integer sort) {
        log.info("调整维度排序，dimensionId：{}，sort：{}", dimensionId, sort);
        adminScaleDimensionService.updateDimensionSort(dimensionId, sort);
        return Result.success();
    }

    @DeleteMapping("/{dimensionId}")
    @PreAuthorize("hasAuthority('scale:dimension:delete')")
    @Operation(summary = "删除维度", description = "维度下存在未删除题目时禁止删除")
    public Result<Void> deleteDimension(
            @PathVariable @NotNull(message = "维度ID不能为空") @Parameter(description = "维度ID", required = true, in = ParameterIn.PATH) Long dimensionId) {
        log.info("删除维度，dimensionId：{}", dimensionId);
        adminScaleDimensionService.deleteDimension(dimensionId);
        return Result.success();
    }

}