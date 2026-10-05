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
import org.lixiyun.pojo.vo.user.scale.ScaleDetailVO;
import org.lixiyun.pojo.vo.user.scale.ScalePrecheckVO;
import org.lixiyun.pojo.vo.user.scale.ScaleVO;
import org.lixiyun.server.service.user.ScaleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 量表前台接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/scale")
@Tag(name = "量表相关接口", description = "量表相关接口")
public class ScaleController {

    private final ScaleService scaleService;

    @GetMapping("/list")
    @Operation(summary = "量表分页列表", description = "按类别、名称关键字过滤，返回启用且有当前生效版本的可作答量表分页列表")
    public Result<PageResult<ScaleVO>> listScales(
            @RequestParam @NotNull(message = "页码不能为空") @NumberOfRanges Integer pageNum,
            @RequestParam @NotNull(message = "每页数量不能为空") @NumberOfRanges Integer pageSize,
            @RequestParam(required = false) @Parameter(description = "量表类别ID", in = ParameterIn.QUERY) Long scaleCategoryId,
            @RequestParam(required = false) @Parameter(description = "量表名称关键字", in = ParameterIn.QUERY) String keyword
    ) {
        log.info("分页查询量表列表: pageNum={}, pageSize={}, scaleCategoryId={}, keyword={}",
                pageNum, pageSize, scaleCategoryId, keyword);
        PageResult<ScaleVO> result = scaleService.listScales(pageNum, pageSize, scaleCategoryId, keyword);
        return Result.success(result);
    }

    @GetMapping("/{scaleId}")
    @Operation(summary = "量表详情", description = "获取量表详情，含当前版本的维度构成与题型统计")
    public Result<ScaleDetailVO> getScaleDetail(
            @PathVariable @NotNull(message = "量表ID不能为空") @Parameter(description = "量表ID", required = true, in = ParameterIn.PATH) Long scaleId
    ) {
        log.info("获取量表详情: scaleId={}", scaleId);
        ScaleDetailVO result = scaleService.getScaleDetail(scaleId);
        return Result.success(result);
    }

    @GetMapping("/{scaleId}/precheck")
    @Operation(summary = "作答前检查", description = "校验量表能否开始作答，返回是否允许、原因及冷却剩余时间")
    public Result<ScalePrecheckVO> precheck(
            @PathVariable @NotNull(message = "量表ID不能为空") @Parameter(description = "量表ID", required = true, in = ParameterIn.PATH) Long scaleId
    ) {
        log.info("量表作答前检查: scaleId={}", scaleId);
        ScalePrecheckVO result = scaleService.precheck(scaleId);
        return Result.success(result);
    }

}
