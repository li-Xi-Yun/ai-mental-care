package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQueryDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleVO;
import org.lixiyun.server.service.admin.AdminScaleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员量表元数据相关接口
 *
 * @author lixiyun
 * @since 2026-04-19 15:03
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale")
@Tag(name = "管理员量表元数据相关接口", description = "管理员量表元数据相关接口")
public class AdminScaleController {

    private final AdminScaleService adminScaleService;

    @PostMapping
//    @PreAuthorize("hasAuthority('scale:metadata:create')")
    @Operation(summary = "创建量表", description = "创建量表元数据信息(不包含题目、选项、规则)，如果指定了分类ID，则更新分类的使用数量")
    public Result<?> createScale(@RequestBody @Validated(AddGroup.class) ScaleDTO scaleDTO) {
        log.info("创建量表：{}", scaleDTO);
        adminScaleService.createScale(scaleDTO);
        return Result.success();
    }

    @PostMapping("/list")
//    @PreAuthorize("hasAuthority('scale:metadata:list')")
    @Operation(summary = "展示量表列表", description = "分页展示所有量表的元数据信息(支持删除状态、启用状态、名称模糊匹配)")
    public Result<PageResult<ScaleVO>> listScales(@RequestBody @Validated AdminScaleQueryDTO queryDTO) {
        log.info("分页查询量表列表: {}", queryDTO);
        PageResult<ScaleVO> result = adminScaleService.listScales(queryDTO);
        return Result.success(result);
    }

    @DeleteMapping("/{scaleId}")
//    @PreAuthorize("hasAuthority('scale:metadata:delete')")
    @Operation(summary = "删除量表", description = "级联删除量表及其题目、选项、规则")
    public Result<Void> deleteScale(@Parameter(description = "量表ID") @PathVariable Long scaleId) {
        log.info("删除量表，scaleId: {}", scaleId);
        adminScaleService.deleteScale(scaleId);
        return Result.success();
    }

    @PutMapping("/{scaleId}")
//    @PreAuthorize("hasAuthority('scale:metadata:update')")
    @Operation(summary = "修改量表", description = "修改量表的元数据信息")
    public Result<Void> updateScale(
            @Parameter(description = "量表ID") @PathVariable Long scaleId,
            @RequestBody @Validated ScaleDTO dto) {
        log.info("修改量表，scaleId: {}, DTO: {}", scaleId, dto);
        adminScaleService.updateScale(scaleId, dto);
        return Result.success();
    }

    @PutMapping("/{scaleId}/restore")
//    @PreAuthorize("hasAuthority('scale:metadata:restore')")
    @Operation(summary = "恢复已删除的量表", description = "级联恢复量表及其题目、选项、规则，将删除状态从1改为0")
    public Result<Void> restoreScale(@Parameter(description = "量表ID") @PathVariable Long scaleId) {
        log.info("恢复已删除的量表，scaleId: {}", scaleId);
        adminScaleService.restoreScale(scaleId);
        return Result.success();
    }
}
