package org.lixiyun.server.controller.user.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.scale.ScaleStartDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleSubmitDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleStartVO;
import org.lixiyun.pojo.vo.user.scale.ScaleSubmitResultVO;
import org.lixiyun.server.service.user.ScaleAssessmentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 量表测评前台接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/scale/assessment")
@Tag(name = "量表测评相关接口", description = "量表测评相关接口")
public class ScaleAssessmentController {

    private final ScaleAssessmentService scaleAssessmentService;

    @PostMapping("/start")
    @Operation(summary = "开始测评", description = "创建测评记录并下发整卷题目与精简跳题规则")
    public Result<ScaleStartVO> start(@RequestBody @Validated ScaleStartDTO dto) {
        log.info("开始测评: scaleId={}", dto.getScaleId());
        ScaleStartVO result = scaleAssessmentService.start(dto);
        return Result.success(result);
    }

    @PostMapping("/submit")
    @Operation(summary = "提交测评答案", description = "批量提交答题明细，服务端完成校验、计分、结果规则匹配与常模换算")
    public Result<ScaleSubmitResultVO> submit(@RequestBody @Validated ScaleSubmitDTO dto) {
        log.info("提交测评答案: recordId={}, 答案数量={}",
                dto.getRecordId(), dto.getAnswers() == null ? 0 : dto.getAnswers().size());
        ScaleSubmitResultVO result = scaleAssessmentService.submit(dto);
        return Result.success(result);
    }

    @PostMapping("/{recordId}/terminate")
    @Operation(summary = "主动终止测评", description = "用户放弃作答或超时退出，将测评记录置为中途终止")
    public Result<Void> terminate(
            @PathVariable @NotNull(message = "测评记录ID不能为空") @Parameter(description = "测评记录ID", required = true, in = ParameterIn.PATH) Long recordId
    ) {
        log.info("主动终止测评: recordId={}", recordId);
        scaleAssessmentService.terminate(recordId);
        return Result.success();
    }

}
