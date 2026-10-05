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
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateApplyDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateCopyDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleOptionTemplateVO;
import org.lixiyun.server.service.admin.AdminScaleOptionTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员量表选项模板相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/option/template")
@Tag(name = "管理员量表选项模板相关接口", description = "管理员量表选项模板相关接口")
public class AdminScaleOptionTemplateController {

    private final AdminScaleOptionTemplateService adminScaleOptionTemplateService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('scale:option-template:list')")
    @Operation(summary = "查询选项模板列表", description = "查询指定量表版本下的选项模板组列表，含明细")
    public Result<List<AdminScaleOptionTemplateVO>> listTemplates(
            @RequestParam @NotNull(message = "量表版本ID不能为空") @Parameter(description = "量表版本ID", required = true, in = ParameterIn.QUERY) Long scaleVersionId) {
        log.info("查询选项模板列表，scaleVersionId：{}", scaleVersionId);
        return Result.success(adminScaleOptionTemplateService.listTemplates(scaleVersionId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('scale:option-template:create')")
    @Operation(summary = "新增选项模板", description = "新增选项模板组及明细")
    public Result<Void> createTemplate(@RequestBody @Validated(AddGroup.class) AdminScaleOptionTemplateDTO dto) {
        log.info("新增选项模板：{}", dto);
        adminScaleOptionTemplateService.createTemplate(dto);
        return Result.success();
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAuthority('scale:option-template:update')")
    @Operation(summary = "修改选项模板", description = "修改选项模板组及明细")
    public Result<Void> updateTemplate(
            @PathVariable @NotNull(message = "模板组ID不能为空") @Parameter(description = "模板组ID", required = true, in = ParameterIn.PATH) Long groupId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleOptionTemplateDTO dto) {
        log.info("修改选项模板，groupId：{}，dto：{}", groupId, dto);
        adminScaleOptionTemplateService.updateTemplate(groupId, dto);
        return Result.success();
    }

    @PutMapping("/{groupId}/apply")
    @PreAuthorize("hasAuthority('scale:option-template:apply')")
    @Operation(summary = "应用模板到题目", description = "把模板明细复制为指定题目的选项，支持覆盖/追加策略")
    public Result<Void> applyTemplate(
            @PathVariable @NotNull(message = "模板组ID不能为空") @Parameter(description = "模板组ID", required = true, in = ParameterIn.PATH) Long groupId,
            @RequestBody @Validated AdminScaleOptionTemplateApplyDTO dto) {
        log.info("应用模板到题目，groupId：{}，题目数量：{}", groupId, dto.getQuestionIds().size());
        adminScaleOptionTemplateService.applyTemplate(groupId, dto);
        return Result.success();
    }

    @PostMapping("/copy")
    @PreAuthorize("hasAuthority('scale:option-template:create')")
    @Operation(summary = "复制模板到目标版本", description = "把模板组整体复制到目标量表版本")
    public Result<Void> copyTemplate(@RequestBody @Validated AdminScaleOptionTemplateCopyDTO dto) {
        log.info("复制选项模板，groupId：{}，targetScaleVersionId：{}", dto.getGroupId(), dto.getTargetScaleVersionId());
        adminScaleOptionTemplateService.copyTemplate(dto);
        return Result.success();
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAuthority('scale:option-template:delete')")
    @Operation(summary = "删除选项模板", description = "逻辑删除选项模板组及明细")
    public Result<Void> deleteTemplate(
            @PathVariable @NotNull(message = "模板组ID不能为空") @Parameter(description = "模板组ID", required = true, in = ParameterIn.PATH) Long groupId) {
        log.info("删除选项模板，groupId：{}", groupId);
        adminScaleOptionTemplateService.deleteTemplate(groupId);
        return Result.success();
    }

}