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
import org.lixiyun.pojo.vo.user.scale.ScaleVO;
import org.lixiyun.server.service.user.ScaleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author lixiyun
 * @since 2026-04-15 09:22
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/scale")
@Tag(name = "量表元数据相关接口", description = "量表元数据相关接口")
public class ScaleController {

    private final ScaleService scaleService;

    @GetMapping("/list")
    @Operation(summary = "展示量表列表", description = "分页展示所有量表的元数据信息(可指定类别ID进行过滤)")
    public Result<PageResult<ScaleVO>> listScales(
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize,
            @RequestParam(required = false) @Parameter(description = "量表分类ID", required = false) Long scaleCategoryId
    ) {
        log.info("分页查询量表列表: {}, {}, {}", pageNum, pageSize, scaleCategoryId);
        PageResult<ScaleVO> result = scaleService.listScales(pageNum, pageSize, scaleCategoryId);
        return Result.success(result);
    }

}
