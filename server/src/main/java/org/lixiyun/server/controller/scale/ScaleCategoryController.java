package org.lixiyun.server.controller.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.scale.ScaleCategoryDTO;
import org.lixiyun.pojo.vo.scale.ScaleCategoryVO;
import org.lixiyun.server.service.ScaleCategoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 14:20
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/scale/category")
@Tag(name = "量表类别相关接口", description = "量表类别相关接口")
public class ScaleCategoryController {

    private final ScaleCategoryService scaleCategoryService;

    @PostMapping
    @Operation(summary = "创建量表类别", description = "创建量表类别，只接收类别名称")
    public Result<Void> createCategory(@RequestBody @Validated ScaleCategoryDTO dto) {
        log.info("创建量表类别：{}", dto);
        scaleCategoryService.createCategory(dto);
        return Result.success();
    }

    @GetMapping
    @Operation(summary = "展示所有量表类别", description = "展示所有量表类别信息")
    public Result<List<ScaleCategoryVO>> listCategories() {
        return Result.success(scaleCategoryService.listCategories());
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "删除量表类别", description = "删除指定量表类别，若当前类别有量表使用，则不允许删除")
    public Result<Void> deleteCategory(@Parameter(description = "量表类别ID") @PathVariable Long categoryId) {
        log.info("删除量表类别，categoryId: {}", categoryId);
        scaleCategoryService.deleteCategory(categoryId);
        return Result.success();
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "修改量表类别", description = "修改指定量表类别的名称")
    public Result<Void> updateCategory(
            @Parameter(description = "量表类别ID") @PathVariable Long categoryId,
            @RequestBody @Validated ScaleCategoryDTO dto) {
        log.info("修改量表类别，categoryId: {}, DTO: {}", categoryId, dto);
        scaleCategoryService.updateCategory(categoryId, dto);
        return Result.success();
    }

}
