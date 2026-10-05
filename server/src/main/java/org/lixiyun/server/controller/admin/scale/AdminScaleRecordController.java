package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleRecordQueryDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleRiskStatisticsQueryDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleAnswerDetailVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleRecordDetailVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleRecordVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleRiskStatisticsVO;
import org.lixiyun.server.service.admin.AdminScaleRecordService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员量表测评记录监控相关接口（敏感数据，须严格权限与审计）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/record")
@Tag(name = "管理员测评记录监控相关接口", description = "管理员测评记录监控相关接口")
public class AdminScaleRecordController {

    private final AdminScaleRecordService adminScaleRecordService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('scale:record:list')")
    @Operation(summary = "分页查询用户测评记录", description = "支持用户ID、量表、量表名称、风险等级、完成状态与时间范围筛选")
    public Result<PageResult<AdminScaleRecordVO>> pageRecords(@RequestBody @Validated AdminScaleRecordQueryDTO queryDTO) {
        log.info("分页查询用户测评记录：{}", queryDTO);
        PageResult<AdminScaleRecordVO> result = adminScaleRecordService.pageRecords(queryDTO);
        return Result.success(result);
    }

    @GetMapping("/{recordId}")
    @PreAuthorize("hasAuthority('scale:record:detail')")
    @Operation(summary = "获取测评记录详情", description = "含维度得分、所用版本与常模组信息")
    public Result<AdminScaleRecordDetailVO> getRecordDetail(
            @PathVariable @NotNull(message = "测评记录ID不能为空") @Parameter(description = "测评记录ID", required = true, in = ParameterIn.PATH) Long recordId) {
        log.info("获取测评记录详情，recordId：{}", recordId);
        return Result.success(adminScaleRecordService.getRecordDetail(recordId));
    }

    @GetMapping("/{recordId}/answers")
    @PreAuthorize("hasAuthority('scale:record:detail')")
    @Operation(summary = "获取测评记录答题明细", description = "返回题目/选项/填空快照，不含单题原始分与耗时")
    public Result<List<AdminScaleAnswerDetailVO>> listAnswerDetails(
            @PathVariable @NotNull(message = "测评记录ID不能为空") @Parameter(description = "测评记录ID", required = true, in = ParameterIn.PATH) Long recordId) {
        log.info("获取测评记录答题明细，recordId：{}", recordId);
        return Result.success(adminScaleRecordService.listAnswerDetails(recordId));
    }

    @PostMapping("/risk/statistics")
    @PreAuthorize("hasAuthority('scale:record:statistics')")
    @Operation(summary = "风险预警统计", description = "按量表与时间范围统计各风险等级人次，高风险占比为派生字段")
    public Result<AdminScaleRiskStatisticsVO> riskStatistics(@RequestBody @Validated AdminScaleRiskStatisticsQueryDTO queryDTO) {
        log.info("风险预警统计：{}", queryDTO);
        return Result.success(adminScaleRecordService.riskStatistics(queryDTO));
    }

}