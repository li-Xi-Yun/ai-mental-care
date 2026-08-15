package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.scale.ScaleOptionBatchAddDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleOptionDTO;
import org.lixiyun.server.service.admin.AdminScaleOptionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 16:27
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/option")
@Tag(name = "管理员量表选项相关接口", description = "管理员量表选项相关接口")
public class AdminScaleOptionController {

    private final AdminScaleOptionService adminScaleOptionService;

    @PostMapping
    @PreAuthorize("hasAuthority('scale:option:create')")
    @Operation(summary = "批量新增选项", description = "为指定题目批量新增选项，需要验证题目是否存在")
    public Result<Void> batchAddOptions(
            @Parameter(description = "批量新增选项信息") @RequestBody @Validated(AddGroup.class) ScaleOptionBatchAddDTO addDTO) {
        log.info("批量新增选项，题目ID: {}, 选项数量: {}", addDTO.getQuestionId(), addDTO.getOptions().size());
        adminScaleOptionService.batchAddOptions(addDTO);
        return Result.success();
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('scale:option:delete')")
    @Operation(summary = "删除选项", description = "删除同一题目下的多个选项")
    public Result<Void> deleteOptions(
            @RequestParam @Parameter(description = "题目ID") @NotNull Long questionId,
            @RequestParam @Parameter(description = "选项ID集合") @NotEmpty List<Long> optionIds) {
        log.info("删除选项，题目ID: {}, 选项ID集合: {}", questionId, optionIds);
        adminScaleOptionService.deleteOptions(questionId, optionIds);
        return Result.success();
    }

    @PutMapping("/update/{optionId}")
    @PreAuthorize("hasAuthority('scale:option:update')")
    @Operation(summary = "更新选项", description = "更新选项的元数据信息")
    public Result<Void> updateOption(
            @PathVariable @Parameter(description = "选项ID") @NotNull Long optionId,
            @RequestBody @Validated(UpdateGroup.class) ScaleOptionDTO updateDTO) {
        log.info("更新选项，选项ID: {}, 更新数据: {}", optionId, updateDTO);
        adminScaleOptionService.updateOption(optionId, updateDTO);
        return Result.success();
    }

}
