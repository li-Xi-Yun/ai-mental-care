package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.user.scale.ScaleQuestionDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleQuestionVO;
import org.lixiyun.server.service.admin.AdminScaleQuestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lixiyun
 * @since 2026-04-19 15:36
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/questions")
@Tag(name = "管理员量表题目相关接口", description = "管理员量表题目相关接口")
public class AdminScaleQuestionController {
    
    private final AdminScaleQuestionService adminScaleQuestionService;

    @PostMapping
    @PreAuthorize("hasAuthority('scale:question:create')")
    @Operation(summary = "创建题目及选项", description = "级联创建题目和选项")
    public Result<?> createQuestionWithOptions(@RequestBody @Validated(AddGroup.class) ScaleQuestionDTO scaleQuestionDTO) {
        log.info("创建题目及选项，请求数据: {}", scaleQuestionDTO);
        adminScaleQuestionService.createWithOptions(scaleQuestionDTO);
        return Result.success();
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('scale:question:list')")
    @Operation(summary = "分页查询量表题目列表", description = "分页展示指定量表下的所有题目及选项，量表需启用且未删除")
    public Result<PageResult<ScaleQuestionVO>> listQuestionsWithOptions(
            @RequestParam @Parameter(description = "量表ID", required = true) @NotNull Long scaleId,
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize
    ) {
        log.info("分页查询量表题目列表，量表ID: {}, 页码: {}, 每页数量: {}", scaleId, pageNum, pageSize);
        PageResult<ScaleQuestionVO> result = adminScaleQuestionService.listQuestionsWithOptions(scaleId, pageNum, pageSize);
        return Result.success(result);
    }

    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasAuthority('scale:question:delete')")
    @Operation(summary = "删除题目及选项", description = "级联删除题目和选项")
    public Result<?> deleteQuestionWithOptions(
            @PathVariable @Parameter(description = "题目ID") @NotNull Long questionId,
            @RequestParam @Parameter(description = "量表ID") @NotNull Long scaleId) {
        log.info("删除题目及选项，题目ID: {}, 量表ID: {}", questionId, scaleId);
        adminScaleQuestionService.deleteWithOptions(questionId, scaleId);
        return Result.success();
    }

    @PutMapping("/{questionId}")
    @PreAuthorize("hasAuthority('scale:question:update')")
    @Operation(summary = "修改题目数据", description = "仅修改题目数据信息，包含选项")
    public Result<?> updateQuestionMetadata(
            @PathVariable @Parameter(description = "题目ID") @NotNull Long questionId,
            @Validated(UpdateGroup.class) @RequestBody ScaleQuestionDTO scaleQuestionDTO) {
        log.info("更新题目数据，题目ID: {}, 请求数据: {}", questionId, scaleQuestionDTO);
        adminScaleQuestionService.updateMetadata(questionId, scaleQuestionDTO);
        return Result.success();
    }
    
}
