package org.lixiyun.server.controller.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.scale.ScaleOptionDTO;
import org.lixiyun.server.service.ScaleOptionService;
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
@RequestMapping("/scale/option")
@Tag(name = "量表选项相关接口", description = "量表选项相关接口")
public class ScaleOptionController {

    private final ScaleOptionService scaleOptionService;

    @DeleteMapping
    @Operation(summary = "删除选项", description = "删除同一题目下的多个选项")
    public Result<Void> deleteOptions(
            @RequestParam @Parameter(description = "题目ID") Long questionId,
            @RequestBody @Parameter(description = "选项ID集合") List<Long> optionIds) {
        log.info("删除选项，题目ID: {}, 选项ID集合: {}", questionId, optionIds);
        scaleOptionService.deleteOptions(questionId, optionIds);
        return Result.success();
    }

    @PutMapping("/update/{optionId}")
    @Operation(summary = "更新选项", description = "更新选项的元数据信息")
    public Result<Void> updateOption(
            @PathVariable @Parameter(description = "选项ID") Long optionId,
            @RequestBody @Validated(UpdateGroup.class) ScaleOptionDTO updateDTO) {
        log.info("更新选项，选项ID: {}, 更新数据: {}", optionId, updateDTO);
        scaleOptionService.updateOption(optionId, updateDTO);
        return Result.success();
    }

}
