package org.lixiyun.server.controller.user.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.user.scale.ScaleUserAnswerDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleUserAnswerVO;
import org.lixiyun.pojo.vo.user.scale.ScaleUserRecordVO;
import org.lixiyun.server.service.user.ScaleUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lixiyun
 * @since 2026-04-17 13:48
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/scale/user")
@Tag(name = "量表用户相关接口", description = "量表用户相关接口")
public class ScaleUserController {

    private final ScaleUserService scaleUserService;

    @PostMapping
    @Operation(summary = "用户测评", description = "用户提交量表测评答案，系统自动计算总分和测评结果")
    public Result<?> submitAnswer(@RequestBody @Validated ScaleUserAnswerDTO answerDTO) {
        log.info("用户提交测评答案：{}", answerDTO);
        scaleUserService.submitAnswer(answerDTO);
        return Result.success();
    }

    @GetMapping("/records")
    @Operation(summary = "测评历史记录查询", description = "分页展示用户的测评历史记录，按创建时间逆序排列")
    public Result<PageResult<ScaleUserRecordVO>> listRecords(
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize
    ) {
        log.info("分页查询测评历史记录: {}, {}", pageNum, pageSize);
        PageResult<ScaleUserRecordVO> result = scaleUserService.listRecords(pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/records/{recordId}/details")
    @Operation(summary = "测评明细信息查询", description = "分页展示指定测评记录的答题明细信息")
    public Result<PageResult<ScaleUserAnswerVO>> listAnswerDetails(
            @Parameter(description = "测评记录ID") @PathVariable Long recordId,
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize
    ) {
        log.info("分页查询测评明细: recordId={}, pageNum={}, pageSize={}", recordId, pageNum, pageSize);
        PageResult<ScaleUserAnswerVO> result = scaleUserService.listAnswerDetails(recordId, pageNum, pageSize);
        return Result.success(result);
    }

}
