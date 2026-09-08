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
import org.lixiyun.pojo.dto.admin.scale.ScaleOptionTemplateDTO;
import org.lixiyun.pojo.vo.admin.scale.ScaleOptionTemplateVO;
import org.lixiyun.server.service.admin.AdminScaleOptionTemplateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-20 21:33
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/option/template")
@Tag(name = "管理员量表选项模板相关接口", description = "管理员量表选项相关接口")
public class AdminScaleOptionTemplateController {

    private final AdminScaleOptionTemplateService adminScaleOptionTemplateService;

    @PostMapping
//    @PreAuthorize("hasAuthority('scale:option-template:create')")
    @Operation(summary = "创建选项模板", description = "创建量表选项模板")
    public Result<Void> createTemplate(@Parameter(description = "选项模板信息") @RequestBody @Validated(AddGroup.class) List<ScaleOptionTemplateDTO> dtoList) {
        log.info("创建选项模板：{}", dtoList);
        adminScaleOptionTemplateService.createTemplate(dtoList);
        return Result.success();
    }

    @GetMapping
//    @PreAuthorize("hasAuthority('scale:option-template:list')")
    @Operation(summary = "查询选项模板列表", description = "根据量表ID查询选项模板列表")
    public Result<List<ScaleOptionTemplateVO>> listTemplates(@Parameter(description = "量表ID") @RequestParam @NotNull Long scaleId) {
        log.info("查询选项模板列表，量表ID: {}", scaleId);
        List<ScaleOptionTemplateVO> result = adminScaleOptionTemplateService.listTemplates(scaleId);
        return Result.success(result);
    }

    @PutMapping
//    @PreAuthorize("hasAuthority('scale:option-template:update')")
    @Operation(summary = "更新选项模板", description = "更新量表选项模板")
    public Result<Void> updateTemplate(
            @Parameter(description = "更新的选项模板信息") @RequestBody @Validated(UpdateGroup.class) List<ScaleOptionTemplateDTO> dtoList) {
        log.info("更新选项模板，templateId: DTO: {}", dtoList);
        adminScaleOptionTemplateService.updateTemplate(dtoList);
        return Result.success();
    }

    @DeleteMapping("/{templateId}")
//    @PreAuthorize("hasAuthority('scale:option-template:delete')")
    @Operation(summary = "删除选项模板", description = "删除量表选项模板")
    public Result<Void> deleteTemplate(@Parameter(description = "选项模板ID") @PathVariable @NotNull Long templateId) {
        log.info("删除选项模板，templateId: {}", templateId);
        adminScaleOptionTemplateService.deleteTemplate(templateId);
        return Result.success();
    }

}
