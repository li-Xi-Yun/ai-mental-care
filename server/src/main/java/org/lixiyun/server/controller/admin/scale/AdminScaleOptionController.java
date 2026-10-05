package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionDeleteDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionSortItemDTO;
import org.lixiyun.server.service.admin.AdminScaleOptionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员量表选项相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/option")
@Tag(name = "管理员量表选项相关接口", description = "管理员量表选项相关接口")
public class AdminScaleOptionController {

    private final AdminScaleOptionService adminScaleOptionService;

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('scale:option:create')")
    @Operation(summary = "批量新增选项", description = "为指定题目批量新增选项")
    public Result<Void> batchAddOptions(@RequestBody @Validated AdminScaleOptionBatchDTO dto) {
        log.info("批量新增选项，题目ID：{}，选项数量：{}", dto.getQuestionId(), dto.getOptions().size());
        adminScaleOptionService.batchAddOptions(dto);
        return Result.success();
    }

    @PutMapping("/{optionId}")
    @PreAuthorize("hasAuthority('scale:option:update')")
    @Operation(summary = "修改单个选项", description = "修改选项文本、分值或排序")
    public Result<Void> updateOption(
            @PathVariable @NotNull(message = "选项ID不能为空") @Parameter(description = "选项ID", required = true, in = ParameterIn.PATH) Long optionId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleOptionDTO dto) {
        log.info("修改单个选项，optionId：{}，dto：{}", optionId, dto);
        adminScaleOptionService.updateOption(optionId, dto);
        return Result.success();
    }

    @PutMapping("/sort")
    @PreAuthorize("hasAuthority('scale:option:update')")
    @Operation(summary = "批量调整选项顺序", description = "整体提交选项排序列表")
    public Result<Void> updateOptionSort(@RequestBody @NotEmpty(message = "排序列表不能为空") @Valid List<AdminScaleOptionSortItemDTO> sortItems) {
        log.info("批量调整选项顺序：{}", sortItems);
        adminScaleOptionService.updateOptionSort(sortItems);
        return Result.success();
    }

    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('scale:option:delete')")
    @Operation(summary = "批量删除选项", description = "删除同一题目下的多个选项，选项归属题目必须一致")
    public Result<Void> batchDeleteOptions(@RequestBody @Validated AdminScaleOptionDeleteDTO dto) {
        log.info("批量删除选项，题目ID：{}，选项数量：{}", dto.getQuestionId(), dto.getOptionIds().size());
        adminScaleOptionService.batchDeleteOptions(dto);
        return Result.success();
    }

}