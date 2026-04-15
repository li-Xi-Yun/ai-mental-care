package org.lixiyun.server.controller.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.scale.ScaleDTO;
import org.lixiyun.pojo.vo.scale.ScaleVO;
import org.lixiyun.server.service.ScaleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lixiyun
 * @since 2026-04-15 09:22
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/scale")
@Tag(name = "量表元数据相关接口", description = "量表元数据相关接口")
public class ScaleController {

    private final ScaleService scaleService;

    @PostMapping
    @Operation(summary = "创建量表", description = "创建量表元数据信息(不包含题目、选项、规则)，如果指定了分类ID，则更新分类的使用数量")
    public Result<?> createScale(@RequestBody @Validated ScaleDTO scaleDTO) {
        log.info("创建量表：{}", scaleDTO);
        scaleService.createScale(scaleDTO);
        return Result.success();
    }

    @GetMapping("/list")
    @Operation(summary = "展示量表列表", description = "分页展示所有量表的元数据信息(可指定类别ID进行过滤)")
    public Result<PageResult<ScaleVO>> listScales(
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize,
            @RequestParam @Parameter(description = "量表分类ID", required = false) Long scaleCategoryId
    ) {
        log.info("分页查询量表列表: {}, {}, {}", pageNum, pageSize, scaleCategoryId);
        PageResult<ScaleVO> result = scaleService.listScales(pageNum, pageSize, scaleCategoryId);
        return Result.success(result);
    }

    @DeleteMapping("/{scaleId}")
    @Operation(summary = "删除量表", description = "级联删除量表及其题目、选项、规则")
    public Result<Void> deleteScale(@Parameter(description = "量表ID") @PathVariable Long scaleId) {
        log.info("删除量表，scaleId: {}", scaleId);
        scaleService.deleteScale(scaleId);
        return Result.success();
    }

    @PutMapping("/{scaleId}")
    @Operation(summary = "修改量表", description = "修改量表的元数据信息")
    public Result<Void> updateScale(
            @Parameter(description = "量表ID") @PathVariable Long scaleId,
            @RequestBody @Validated ScaleDTO dto) {
        log.info("修改量表，scaleId: {}, DTO: {}", scaleId, dto);
        scaleService.updateScale(scaleId, dto);
        return Result.success();
    }

}
