package org.lixiyun.server.controller.user.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.conversation.EmotionDiagnosisFeedbackDTO;
import org.lixiyun.pojo.vo.user.conversation.AssessmentFeedbackVO;
import org.lixiyun.server.service.user.AssessmentFeedbackService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 诊断反馈控制器
 *
 * <p>承接所有关于诊断书反馈的接口：反馈查询、反馈提交/更新、反馈删除。</p>
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/conversation/assessment-feedback")
@Tag(name = "诊断反馈相关接口", description = "诊断反馈查询、提交/更新、删除等接口")
public class AssessmentFeedbackController {

    private final AssessmentFeedbackService assessmentFeedbackService;

    @GetMapping("/{diagnosisId}")
    @Operation(summary = "查询诊断反馈详情", description = "按诊断书ID查询当前用户对该诊断书的反馈信息，未反馈时返回null")
    public Result<AssessmentFeedbackVO> getFeedback(
            @PathVariable @Parameter(description = "诊断书ID", required = true) @NotNull Long diagnosisId
    ) {
        log.info("查询诊断反馈: {}", diagnosisId);
        AssessmentFeedbackVO result = assessmentFeedbackService.getByDiagnosisId(diagnosisId);
        return Result.success(result);
    }

    @PutMapping("/{diagnosisId}")
    @Operation(summary = "提交或更新诊断反馈", description = "按诊断书ID提交反馈信息（打分、认同度、采纳情况等），已存在则更新，否则新增")
    public Result<Void> saveFeedback(
            @PathVariable @Parameter(description = "诊断书ID", required = true) @NotNull Long diagnosisId,
            @RequestBody @Valid EmotionDiagnosisFeedbackDTO feedbackDTO
    ) {
        log.info("提交/更新诊断反馈, diagnosisId={}, feedbackDTO={}", diagnosisId, feedbackDTO);
        assessmentFeedbackService.saveOrUpdate(diagnosisId, feedbackDTO);
        return Result.success();
    }

    @DeleteMapping("/{diagnosisId}")
    @Operation(summary = "删除诊断反馈", description = "按诊断书ID逻辑删除当前用户的诊断反馈记录")
    public Result<Void> deleteFeedback(
            @PathVariable @Parameter(description = "诊断书ID", required = true) @NotNull Long diagnosisId
    ) {
        log.info("删除诊断反馈: {}", diagnosisId);
        assessmentFeedbackService.deleteByDiagnosisId(diagnosisId);
        return Result.success();
    }

}