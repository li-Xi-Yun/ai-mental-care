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
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormDeleteDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormGroupDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleNormGroupVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleNormVO;
import org.lixiyun.server.service.admin.AdminScaleNormService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员量表常模相关接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/scale/norm")
@Tag(name = "管理员量表常模相关接口", description = "管理员量表常模相关接口")
public class AdminScaleNormController {

    private final AdminScaleNormService adminScaleNormService;

    @GetMapping("/group/list")
    @PreAuthorize("hasAuthority('scale:norm:list')")
    @Operation(summary = "查询常模组列表", description = "查询指定量表版本下的常模组列表，含人口学筛选条件与公式参数")
    public Result<List<AdminScaleNormGroupVO>> listNormGroups(
            @RequestParam @NotNull(message = "量表版本ID不能为空") @Parameter(description = "量表版本ID", required = true, in = ParameterIn.QUERY) Long scaleVersionId) {
        log.info("查询常模组列表，scaleVersionId：{}", scaleVersionId);
        return Result.success(adminScaleNormService.listNormGroups(scaleVersionId));
    }

    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasAuthority('scale:norm:list')")
    @Operation(summary = "获取常模组详情", description = "获取常模组详情，含其常模明细")
    public Result<AdminScaleNormGroupVO> getNormGroupDetail(
            @PathVariable @NotNull(message = "常模组ID不能为空") @Parameter(description = "常模组ID", required = true, in = ParameterIn.PATH) Long groupId) {
        log.info("获取常模组详情，groupId：{}", groupId);
        return Result.success(adminScaleNormService.getNormGroupDetail(groupId));
    }

    @PostMapping("/group")
    @PreAuthorize("hasAuthority('scale:norm:create')")
    @Operation(summary = "新增常模组", description = "新增常模组，公式法需提供 mean/sd，查表法需后续维护明细")
    public Result<Void> createNormGroup(@RequestBody @Validated(AddGroup.class) AdminScaleNormGroupDTO dto) {
        log.info("新增常模组：{}", dto);
        adminScaleNormService.createNormGroup(dto);
        return Result.success();
    }

    @PutMapping("/group/{groupId}")
    @PreAuthorize("hasAuthority('scale:norm:update')")
    @Operation(summary = "修改常模组", description = "修改常模组信息与计算方式，查表法需保证明细完整")
    public Result<Void> updateNormGroup(
            @PathVariable @NotNull(message = "常模组ID不能为空") @Parameter(description = "常模组ID", required = true, in = ParameterIn.PATH) Long groupId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleNormGroupDTO dto) {
        log.info("修改常模组，groupId：{}，dto：{}", groupId, dto);
        adminScaleNormService.updateNormGroup(groupId, dto);
        return Result.success();
    }

    @DeleteMapping("/group/{groupId}")
    @PreAuthorize("hasAuthority('scale:norm:delete')")
    @Operation(summary = "删除常模组", description = "级联逻辑删除常模组及其明细，被测评记录引用时禁止删除")
    public Result<Void> deleteNormGroup(
            @PathVariable @NotNull(message = "常模组ID不能为空") @Parameter(description = "常模组ID", required = true, in = ParameterIn.PATH) Long groupId) {
        log.info("删除常模组，groupId：{}", groupId);
        adminScaleNormService.deleteNormGroup(groupId);
        return Result.success();
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('scale:norm:list')")
    @Operation(summary = "查询常模明细分页列表", description = "查询指定常模组下的常模明细全量列表")
    public Result<List<AdminScaleNormVO>> listNorms(
            @RequestParam @NotNull(message = "常模组ID不能为空") @Parameter(description = "常模组ID", required = true, in = ParameterIn.QUERY) Long normGroupId) {
        log.info("查询常模明细列表，normGroupId：{}", normGroupId);
        return Result.success(adminScaleNormService.listNorms(normGroupId));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('scale:norm:create')")
    @Operation(summary = "批量保存常模明细", description = "批量新增/更新常模明细，按唯一键 upsert")
    public Result<Void> batchSaveNorms(@RequestBody @Validated AdminScaleNormBatchDTO dto) {
        log.info("批量保存常模明细，normGroupId：{}，明细数量：{}", dto.getNormGroupId(), dto.getItems().size());
        adminScaleNormService.batchSaveNorms(dto);
        return Result.success();
    }

    @PutMapping("/{normId}")
    @PreAuthorize("hasAuthority('scale:norm:update')")
    @Operation(summary = "修改常模明细", description = "修改单条常模明细")
    public Result<Void> updateNorm(
            @PathVariable @NotNull(message = "常模明细ID不能为空") @Parameter(description = "常模明细ID", required = true, in = ParameterIn.PATH) Long normId,
            @RequestBody @Validated(UpdateGroup.class) AdminScaleNormDTO dto) {
        log.info("修改常模明细，normId：{}，dto：{}", normId, dto);
        adminScaleNormService.updateNorm(normId, dto);
        return Result.success();
    }

    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('scale:norm:delete')")
    @Operation(summary = "批量删除常模明细", description = "删除指定常模组下的多条常模明细")
    public Result<Void> batchDeleteNorms(@RequestBody @Validated AdminScaleNormDeleteDTO dto) {
        log.info("批量删除常模明细，normGroupId：{}，数量：{}", dto.getNormGroupId(), dto.getNormIds().size());
        adminScaleNormService.batchDeleteNorms(dto);
        return Result.success();
    }

}