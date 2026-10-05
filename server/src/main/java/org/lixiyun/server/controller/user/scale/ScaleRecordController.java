package org.lixiyun.server.controller.user.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.vo.user.scale.ScaleAnswerDetailVO;
import org.lixiyun.pojo.vo.user.scale.ScaleRecordDetailVO;
import org.lixiyun.pojo.vo.user.scale.ScaleRecordVO;
import org.lixiyun.server.service.user.ScaleRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测评记录前台接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/scale/record")
@Tag(name = "测评记录相关接口", description = "测评记录相关接口")
public class ScaleRecordController {

    private final ScaleRecordService scaleRecordService;

    @GetMapping("/list")
    @Operation(summary = "我的测评历史分页", description = "分页查询当前用户的测评记录，可按量表与完成状态过滤，按开始时间倒序")
    public Result<PageResult<ScaleRecordVO>> listRecords(
            @RequestParam @NotNull(message = "页码不能为空") @NumberOfRanges Integer pageNum,
            @RequestParam @NotNull(message = "每页数量不能为空") @NumberOfRanges Integer pageSize,
            @RequestParam(required = false) @Parameter(description = "量表ID", in = ParameterIn.QUERY) Long scaleId,
            @RequestParam(required = false) @Parameter(description = "完成状态：0-未完成 1-已完成 2-中途终止", in = ParameterIn.QUERY) Integer finishStatus
    ) {
        log.info("分页查询我的测评历史: pageNum={}, pageSize={}, scaleId={}, finishStatus={}",
                pageNum, pageSize, scaleId, finishStatus);
        PageResult<ScaleRecordVO> result = scaleRecordService.listRecords(pageNum, pageSize, scaleId, finishStatus);
        return Result.success(result);
    }

    @GetMapping("/{recordId}")
    @Operation(summary = "单次测评结果详情", description = "查询当前用户指定测评记录的完整结果与维度得分")
    public Result<ScaleRecordDetailVO> getRecordDetail(
            @PathVariable @NotNull(message = "测评记录ID不能为空") @Parameter(description = "测评记录ID", required = true, in = ParameterIn.PATH) Long recordId
    ) {
        log.info("查询测评结果详情: recordId={}", recordId);
        ScaleRecordDetailVO result = scaleRecordService.getRecordDetail(recordId);
        return Result.success(result);
    }

    @GetMapping("/{recordId}/answers")
    @Operation(summary = "答题明细", description = "查询当前用户指定测评记录下的逐题答题明细")
    public Result<List<ScaleAnswerDetailVO>> listAnswerDetails(
            @PathVariable @NotNull(message = "测评记录ID不能为空") @Parameter(description = "测评记录ID", required = true, in = ParameterIn.PATH) Long recordId
    ) {
        log.info("查询答题明细: recordId={}", recordId);
        List<ScaleAnswerDetailVO> result = scaleRecordService.listAnswerDetails(recordId);
        return Result.success(result);
    }

}
