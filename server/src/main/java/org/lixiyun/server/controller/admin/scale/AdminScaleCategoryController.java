package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.scale.ScaleCategoryDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleCategoryVO;
import org.lixiyun.server.service.admin.AdminScaleCategoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-19 14:48
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/category")
@Tag(name = "管理员量表类别相关接口", description = "管理员量表类别相关接口")
public class AdminScaleCategoryController {

    private final AdminScaleCategoryService adminScaleCategoryService;

    @GetMapping
//    @PreAuthorize("hasAuthority('scale:category:list')")
    @Operation(summary = "展示所有量表类别", description = "展示所有量表类别信息")
    public Result<List<ScaleCategoryVO>> listCategories() {
        List<ScaleCategoryVO> result = adminScaleCategoryService.listCategories();
        return Result.success(result);
    }

    @PostMapping
//    @PreAuthorize("hasAuthority('scale:category:create')")
    @Operation(summary = "创建量表类别", description = "创建量表类别，只接收类别名称")
    public Result<Void> createCategory(@RequestBody @Validated ScaleCategoryDTO dto) {
        log.info("创建量表类别：{}", dto);
        adminScaleCategoryService.createCategory(dto);
        return Result.success();
    }

    @DeleteMapping("/{categoryId}")
//    @PreAuthorize("hasAuthority('scale:category:delete')")
    @Operation(summary = "删除量表类别", description = "删除指定量表类别，若当前类别有量表使用，则不允许删除")
    public Result<Void> deleteCategory(@Parameter(description = "量表类别ID") @PathVariable Long categoryId) {
        log.info("删除量表类别，categoryId: {}", categoryId);
        adminScaleCategoryService.deleteCategory(categoryId);
        return Result.success();
    }

    @PutMapping("/{categoryId}")
//    @PreAuthorize("hasAuthority('scale:category:update')")
    @Operation(summary = "修改量表类别", description = "修改指定量表类别的名称")
    public Result<Void> updateCategory(
            @Parameter(description = "量表类别ID") @PathVariable Long categoryId,
            @RequestBody @Validated ScaleCategoryDTO dto) {
        log.info("修改量表类别，categoryId: {}, DTO: {}", categoryId, dto);
        adminScaleCategoryService.updateCategory(categoryId, dto);
        return Result.success();
    }


}
