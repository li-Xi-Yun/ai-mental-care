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
import org.lixiyun.pojo.dto.admin.scale.AdminScaleBranchRuleBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleBranchRuleDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleBranchRuleVO;
import org.lixiyun.server.service.admin.AdminScaleBranchRuleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员量表跳题规则相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/branch-rule")
@Tag(name = "管理员量表跳题规则相关接口", description = "管理员量表跳题规则相关接口")
public class AdminScaleBranchRuleController {

    private final AdminScaleBranchRuleService adminScaleBranchRuleService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('scale:branch-rule:list')")
    @Operation(summary = "查询跳题规则列表", description = "查询指定量表版本下的跳题规则列表")
    public Result<List<AdminScaleBranchRuleVO>> listRules(
            @RequestParam @NotNull(message = "量表版本ID不能为空") @Parameter(description = "量表版本ID", required = true, in = ParameterIn.QUERY) Long scaleVersionId) {
        log.info("查询跳题规则列表，scaleVersionId：{}", scaleVersionId);
        return Result.success(adminScaleBranchRuleService.listRules(scaleVersionId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('scale:branch-rule:create')")
    @Operation(summary = "新增跳题规则", description = "新增单条跳题规则")
    public Result<Void> createRule(@RequestBody @Validated(AddGroup.class) AdminScaleBranchRuleDTO dto) {
        log.info("新增跳题规则：{}", dto);
        adminScaleBranchRuleService.createRule(dto);
        return Result.success();
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('scale:branch-rule:update')")
    @Operation(summary = "批量保存跳题规则", description = "整个版本的规则集合整体提交，服务端做差异更新")
    public Result<Void> batchSaveRules(@RequestBody @Validated AdminScaleBranchRuleBatchDTO dto) {
        log.info("批量保存跳题规则，scaleVersionId：{}，规则数量：{}", dto.getScaleVersionId(), dto.getRules().size());
        adminScaleBranchRuleService.batchSaveRules(dto);
        return Result.success();
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('scale:branch-rule:update')")
    @Operation(summary = "修改跳题规则", description = "修改单条跳题规则")
    public Result<Void> updateRule(
            @PathVariable @NotNull(message = "规则ID不能为空") @Parameter(description = "规则ID", required = true, in = ParameterIn.PATH) Long ruleId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleBranchRuleDTO dto) {
        log.info("修改跳题规则，ruleId：{}，dto：{}", ruleId, dto);
        adminScaleBranchRuleService.updateRule(ruleId, dto);
        return Result.success();
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('scale:branch-rule:delete')")
    @Operation(summary = "删除跳题规则", description = "删除单条跳题规则")
    public Result<Void> deleteRule(
            @PathVariable @NotNull(message = "规则ID不能为空") @Parameter(description = "规则ID", required = true, in = ParameterIn.PATH) Long ruleId) {
        log.info("删除跳题规则，ruleId：{}", ruleId);
        adminScaleBranchRuleService.deleteRule(ruleId);
        return Result.success();
    }

}