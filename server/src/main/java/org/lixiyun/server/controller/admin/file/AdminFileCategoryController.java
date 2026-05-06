package org.lixiyun.server.controller.admin.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.admin.file.FileCategoryAddDTO;
import org.lixiyun.pojo.dto.admin.file.FileCategoryUpdateDTO;
import org.lixiyun.pojo.vo.admin.file.FileCategoryVO;
import org.lixiyun.server.service.admin.AdminFileCategoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件分类管理相关接口
 *
 * @author lixiyun
 * @since 2026-05-01 16:00
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/file/category")
@Tag(name = "文件分类相关接口", description = "文件分类相关接口")
public class AdminFileCategoryController {

    private final AdminFileCategoryService adminFileCategoryService;

    @PostMapping
    @Operation(summary = "新增文件分类", description = "新增文件分类，支持指定父级分类ID创建子分类")
    public Result<FileCategoryVO> addCategory(@RequestBody @Validated FileCategoryAddDTO addDTO) {
        log.info("新增文件分类请求接口：{}", addDTO);
        FileCategoryVO categoryVO = adminFileCategoryService.addCategory(addDTO);
        return Result.success(categoryVO);
    }

    @PutMapping
    @Operation(summary = "修改文件分类", description = "修改文件分类信息")
    public Result<FileCategoryVO> updateCategory(@RequestBody @Validated FileCategoryUpdateDTO updateDTO) {
        log.info("修改文件分类请求接口：{}", updateDTO);
        FileCategoryVO categoryVO = adminFileCategoryService.updateCategory(updateDTO);
        return Result.success(categoryVO);
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "删除文件分类", description = "删除文件分类，如果该分类下存在文件(包含子分类)则报错，同时级联删除子分类")
    public Result<Void> deleteCategory(
            @PathVariable @NotNull @Parameter(description = "分类ID", required = true, in = ParameterIn.PATH) Long categoryId) {
        log.info("删除文件分类请求接口，分类ID：{}", categoryId);
        adminFileCategoryService.deleteCategory(categoryId);
        return Result.success();
    }

    @GetMapping("/sub/{categoryId}")
    @Operation(summary = "查询子分类列表", description = "查询指定分类下的所有子分类，不传分类ID则查询所有一级分类（树形结构）")
    public Result<List<FileCategoryVO>> listSubCategories(
            @PathVariable(required = false) @Parameter(description = "分类ID（可选，为空则查询所有一级分类）", in = ParameterIn.PATH) Long categoryId) {
        log.info("查询子分类列表请求接口，分类ID：{}", categoryId);
        List<FileCategoryVO> subCategoryList = adminFileCategoryService.listSubCategories(categoryId);
        return Result.success(subCategoryList);
    }

    @GetMapping
    @Operation(summary = "查询所有分类", description = "查询所有文件分类信息（列表结构）")
    public Result<List<FileCategoryVO>> listAllCategories() {
        log.info("查询所有文件分类请求接口");
        List<FileCategoryVO> categoryList = adminFileCategoryService.listAllCategories();
        return Result.success(categoryList);
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "查询分类详情", description = "根据分类ID查询分类详细信息")
    public Result<FileCategoryVO> getCategoryDetail(
            @PathVariable @NotNull @Parameter(description = "分类ID", required = true, in = ParameterIn.PATH) Long categoryId) {
        log.info("查询分类详情请求接口，分类ID：{}", categoryId);
        FileCategoryVO categoryVO = adminFileCategoryService.getCategoryDetail(categoryId);
        return Result.success(categoryVO);
    }
}
