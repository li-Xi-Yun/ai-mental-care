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
import org.lixiyun.pojo.dto.admin.scale.AdminScaleCategoryDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleCategoryQueryDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleCategoryVO;
import org.lixiyun.server.service.admin.AdminScaleCategoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员量表类别相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/category")
@Tag(name = "管理员量表类别相关接口", description = "管理员量表类别相关接口")
public class AdminScaleCategoryController {

    private final AdminScaleCategoryService adminScaleCategoryService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('scale:category:list')")
    @Operation(summary = "分页查询量表类别", description = "支持类别名称模糊匹配、删除状态筛选")
    public Result<PageResult<AdminScaleCategoryVO>> pageCategories(@RequestBody @Validated AdminScaleCategoryQueryDTO queryDTO) {
        log.info("分页查询量表类别：{}", queryDTO);
        PageResult<AdminScaleCategoryVO> result = adminScaleCategoryService.pageCategory(queryDTO);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('scale:category:list')")
    @Operation(summary = "获取量表类别详情", description = "根据ID获取量表类别详细信息")
    public Result<AdminScaleCategoryVO> getCategoryDetail(
            @PathVariable @NotNull @Parameter(description = "类别ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("获取量表类别详情，id：{}", id);
        AdminScaleCategoryVO vo = adminScaleCategoryService.getCategoryDetail(id);
        return Result.success(vo);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('scale:category:create')")
    @Operation(summary = "新增量表类别", description = "新增一条量表类别记录")
    public Result<Void> createCategory(@RequestBody @Validated(AddGroup.class) AdminScaleCategoryDTO dto) {
        log.info("新增量表类别：{}", dto);
        adminScaleCategoryService.createCategory(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('scale:category:update')")
    @Operation(summary = "修改量表类别", description = "修改量表类别记录")
    public Result<Void> updateCategory(
            @PathVariable @NotNull @Parameter(description = "类别ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleCategoryDTO dto) {
        log.info("修改量表类别，id：{}，dto：{}", id, dto);
        adminScaleCategoryService.updateCategory(id, dto);
        return Result.success();
    }

    @PutMapping("/{id}/sort")
    @PreAuthorize("hasAuthority('scale:category:update')")
    @Operation(summary = "调整量表类别排序", description = "只更新排序字段")
    public Result<Void> updateCategorySort(
            @PathVariable @NotNull @Parameter(description = "类别ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestParam @NotNull @Parameter(description = "排序值", required = true) Integer sort) {
        log.info("调整量表类别排序，id：{}，sort：{}", id, sort);
        adminScaleCategoryService.updateCategorySort(id, sort);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('scale:category:delete')")
    @Operation(summary = "删除量表类别", description = "逻辑删除，该类别下存在未删除量表时禁止删除")
    public Result<Void> deleteCategory(
            @PathVariable @NotNull @Parameter(description = "类别ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("删除量表类别，id：{}", id);
        adminScaleCategoryService.deleteCategory(id);
        return Result.success();
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('scale:category:update')")
    @Operation(summary = "恢复量表类别", description = "恢复已删除的量表类别")
    public Result<Void> restoreCategory(
            @PathVariable @NotNull @Parameter(description = "类别ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("恢复量表类别，id：{}", id);
        adminScaleCategoryService.restoreCategory(id);
        return Result.success();
    }
}