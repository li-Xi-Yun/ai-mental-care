package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleResultRuleBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleResultRuleDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleResultRuleVO;
import org.lixiyun.server.service.admin.AdminScaleResultRuleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员量表结果规则相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/result-rule")
@Tag(name = "管理员量表结果规则相关接口", description = "管理员量表结果规则相关接口")
public class AdminScaleResultRuleController {

    private final AdminScaleResultRuleService adminScaleResultRuleService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('scale:result-rule:list')")
    @Operation(summary = "查询结果规则列表", description = "查询指定量表版本下的结果规则列表，按维度分组，dimensionId 为 NULL 的为总分规则")
    public Result<List<AdminScaleResultRuleVO>> listRules(
            @RequestParam @NotNull(message = "量表版本ID不能为空") @Parameter(description = "量表版本ID", required = true, in = ParameterIn.QUERY) Long scaleVersionId) {
        log.info("查询结果规则列表，scaleVersionId：{}", scaleVersionId);
        return Result.success(adminScaleResultRuleService.listRules(scaleVersionId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('scale:result-rule:create')")
    @Operation(summary = "新增结果规则", description = "新增单条结果规则")
    public Result<Void> createRule(@RequestBody @Validated(AddGroup.class) AdminScaleResultRuleDTO dto) {
        log.info("新增结果规则：{}", dto);
        adminScaleResultRuleService.createRule(dto);
        return Result.success();
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('scale:result-rule:update')")
    @Operation(summary = "批量保存结果规则", description = "整体维护一个维度的区间集，服务端做差异更新并校验区间不重叠")
    public Result<Void> batchSaveRules(@RequestBody @Validated AdminScaleResultRuleBatchDTO dto) {
        log.info("批量保存结果规则，scaleVersionId：{}，规则数量：{}", dto.getScaleVersionId(), dto.getRules().size());
        adminScaleResultRuleService.batchSaveRules(dto);
        return Result.success();
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('scale:result-rule:update')")
    @Operation(summary = "修改结果规则", description = "修改单条结果规则")
    public Result<Void> updateRule(
            @PathVariable @NotNull(message = "规则ID不能为空") @Parameter(description = "规则ID", required = true, in = ParameterIn.PATH) Long ruleId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleResultRuleDTO dto) {
        log.info("修改结果规则，ruleId：{}，dto：{}", ruleId, dto);
        adminScaleResultRuleService.updateRule(ruleId, dto);
        return Result.success();
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('scale:result-rule:delete')")
    @Operation(summary = "删除结果规则", description = "删除单条结果规则")
    public Result<Void> deleteRule(
            @PathVariable @NotNull(message = "规则ID不能为空") @Parameter(description = "规则ID", required = true, in = ParameterIn.PATH) Long ruleId) {
        log.info("删除结果规则，ruleId：{}", ruleId);
        adminScaleResultRuleService.deleteRule(ruleId);
        return Result.success();
    }

}