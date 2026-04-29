package org.lixiyun.server.controller.admin.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.user.scale.ScaleResultRuleDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleResultRuleVO;
import org.lixiyun.server.service.admin.AdminScaleRuleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 15:34
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/rule")
@Tag(name = "管理员量表结果规则相关接口", description = "管理员量表结果规则相关接口")
public class AdminScaleRuleController {

    private final AdminScaleRuleService adminScaleRuleService;

    @PostMapping
    @PreAuthorize("hasAuthority('scale:rule:create')")
    @Operation(summary = "创建量表结果规则", description = "创建量表结果规则")
    public Result<Void> createRule(@Parameter(description = "规则信息") @RequestBody @Validated(AddGroup.class) ScaleResultRuleDTO dto) {
        log.info("创建规则：{}", dto);
        adminScaleRuleService.createRule(dto);
        return Result.success();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('scale:rule:list')")
    @Operation(summary = "展示量表结果规则", description = "只展示规则本身, 不展示量表信息")
    public Result<List<ScaleResultRuleVO>> listRules(@Parameter(description = "量表ID") @RequestParam @NotNull Long scaleId) {
        log.info("查询规则列表，量表ID: {}", scaleId);
        List<ScaleResultRuleVO> result = adminScaleRuleService.listRules(scaleId);
        return Result.success(result);
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('scale:rule:update')")
    @Operation(summary = "修改量表结果规则", description = "只修改规则本身")
    public Result<Void> updateRule(
            @Parameter(description = "规则ID") @PathVariable @NotNull Long ruleId,
            @Parameter(description = "更新的规则信息") @RequestBody @Validated(UpdateGroup.class) ScaleResultRuleDTO dto) {
        log.info("修改规则，ruleId: {}, DTO: {}", ruleId, dto);
        adminScaleRuleService.updateRule(ruleId, dto);
        return Result.success();
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('scale:rule:delete')")
    @Operation(summary = "删除量表结果规则", description = "只删除规则本身")
    public Result<Void> deleteRule(@Parameter(description = "规则ID") @PathVariable @NotNull Long ruleId) {
        log.info("删除规则，ruleId: {}", ruleId);
        adminScaleRuleService.deleteRule(ruleId);
        return Result.success();
    }

}
