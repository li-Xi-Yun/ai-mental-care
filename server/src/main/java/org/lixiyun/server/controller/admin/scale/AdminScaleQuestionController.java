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
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQuestionDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQuestionSortItemDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleQuestionVO;
import org.lixiyun.server.service.admin.AdminScaleQuestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员量表题目相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/question")
@Tag(name = "管理员量表题目相关接口", description = "管理员量表题目相关接口")
public class AdminScaleQuestionController {

    private final AdminScaleQuestionService adminScaleQuestionService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('scale:question:list')")
    @Operation(summary = "查询题目列表", description = "查询指定量表版本下的题目全量列表，含选项，按排序值升序")
    public Result<List<AdminScaleQuestionVO>> listQuestions(
            @RequestParam @NotNull(message = "量表版本ID不能为空") @Parameter(description = "量表版本ID", required = true, in = ParameterIn.QUERY) Long scaleVersionId) {
        log.info("查询题目列表，scaleVersionId：{}", scaleVersionId);
        return Result.success(adminScaleQuestionService.listQuestions(scaleVersionId));
    }

    @GetMapping("/{questionId}")
    @PreAuthorize("hasAuthority('scale:question:list')")
    @Operation(summary = "获取题目详情", description = "根据题目ID获取题目及选项的详细信息")
    public Result<AdminScaleQuestionVO> getQuestionDetail(
            @PathVariable @NotNull(message = "题目ID不能为空") @Parameter(description = "题目ID", required = true, in = ParameterIn.PATH) Long questionId) {
        log.info("获取题目详情，questionId：{}", questionId);
        return Result.success(adminScaleQuestionService.getQuestionDetail(questionId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('scale:question:create')")
    @Operation(summary = "新增题目", description = "新增题目，可内联选项")
    public Result<Void> createQuestion(@RequestBody @Validated(AddGroup.class) AdminScaleQuestionDTO dto) {
        log.info("新增题目：{}", dto);
        adminScaleQuestionService.createQuestion(dto);
        return Result.success();
    }

    @PutMapping("/{questionId}")
    @PreAuthorize("hasAuthority('scale:question:update')")
    @Operation(summary = "修改题目", description = "修改题目，可整体替换选项")
    public Result<Void> updateQuestion(
            @PathVariable @NotNull(message = "题目ID不能为空") @Parameter(description = "题目ID", required = true, in = ParameterIn.PATH) Long questionId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleQuestionDTO dto) {
        log.info("修改题目，questionId：{}，dto：{}", questionId, dto);
        adminScaleQuestionService.updateQuestion(questionId, dto);
        return Result.success();
    }

    @PutMapping("/sort")
    @PreAuthorize("hasAuthority('scale:question:update')")
    @Operation(summary = "批量调整题目顺序", description = "整体提交题目排序列表")
    public Result<Void> updateQuestionSort(@RequestBody @NotEmpty(message = "排序列表不能为空") @Valid List<AdminScaleQuestionSortItemDTO> sortItems) {
        log.info("批量调整题目顺序：{}", sortItems);
        adminScaleQuestionService.updateQuestionSort(sortItems);
        return Result.success();
    }

    @PutMapping("/{questionId}/dimension")
    @PreAuthorize("hasAuthority('scale:question:update')")
    @Operation(summary = "调整题目所属维度", description = "dimensionId 为空表示移除维度归属")
    public Result<Void> updateQuestionDimension(
            @PathVariable @NotNull(message = "题目ID不能为空") @Parameter(description = "题目ID", required = true, in = ParameterIn.PATH) Long questionId,
            @RequestParam(required = false) @Parameter(description = "目标维度ID，可空", in = ParameterIn.QUERY) Long dimensionId) {
        log.info("调整题目所属维度，questionId：{}，dimensionId：{}", questionId, dimensionId);
        adminScaleQuestionService.updateQuestionDimension(questionId, dimensionId);
        return Result.success();
    }

    @PutMapping("/{questionId}/copy")
    @PreAuthorize("hasAuthority('scale:question:create')")
    @Operation(summary = "复制题目到指定版本", description = "将题目及选项复制到目标量表版本")
    public Result<Void> copyQuestion(
            @PathVariable @NotNull(message = "题目ID不能为空") @Parameter(description = "题目ID", required = true, in = ParameterIn.PATH) Long questionId,
            @RequestParam @NotNull(message = "目标量表版本ID不能为空") @Parameter(description = "目标量表版本ID", required = true, in = ParameterIn.QUERY) Long targetScaleVersionId) {
        log.info("复制题目，questionId：{}，targetScaleVersionId：{}", questionId, targetScaleVersionId);
        adminScaleQuestionService.copyQuestion(questionId, targetScaleVersionId);
        return Result.success();
    }

    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasAuthority('scale:question:delete')")
    @Operation(summary = "删除题目", description = "级联逻辑删除题目及选项，有跳题规则引用时需同步清理")
    public Result<Void> deleteQuestion(
            @PathVariable @NotNull(message = "题目ID不能为空") @Parameter(description = "题目ID", required = true, in = ParameterIn.PATH) Long questionId) {
        log.info("删除题目，questionId：{}", questionId);
        adminScaleQuestionService.deleteQuestion(questionId);
        return Result.success();
    }

}