package org.lixiyun.server.controller.admin.permission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.dto.admin.permission.SysPermissionDTO;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionDetailVO;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionTreeVO;
import org.lixiyun.server.service.admin.SysPermissionService;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统权限管理控制器
 * <p>
 * 提供权限的增删改查、权限树查询等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31 11:39
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/permission")
@Tag(name = "系统权限管理接口", description = "系统权限管理接口")
public class SysPermissionController {

    private final SysPermissionService sysPermissionService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('sys:permission:list')")
    @Operation(summary = "查询权限树形列表", description = "返回权限树形列表，用于角色授权弹窗、临时权限授予弹窗勾选树")
    public Result<List<SysPermissionTreeVO>> getPermissionTree() {
        log.info("查询权限树形列表");
        List<SysPermissionTreeVO> permissionTree = sysPermissionService.getPermissionTree();
        return Result.success(permissionTree);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:permission:detail')")
    @Operation(summary = "查询单个权限详情", description = "根据权限ID查询权限详细信息")
    public Result<SysPermissionDetailVO> getPermissionDetail(
            @PathVariable @Parameter(description = "权限ID") Long id) {
        log.info("查询权限详情，权限ID：{}", id);
        SysPermissionDetailVO permissionDetail = sysPermissionService.getPermissionDetail(id);
        return Result.success(permissionDetail);
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('sys:permission:add')")
    @Operation(summary = "新增权限", description = "新增权限点，权限标识符必须唯一")
    public Result<Void> addPermission(@RequestBody @Validated(AddGroup.class) SysPermissionDTO permissionDTO) {
        log.info("新增权限：{}", permissionDTO);
        sysPermissionService.addPermission(permissionDTO);
        return Result.success();
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('sys:permission:update')")
    @Operation(summary = "修改权限信息", description = "修改权限标识符、名称、分组等信息")
    public Result<Void> updatePermission(
            @PathVariable @Parameter(description = "权限ID") Long id,
            @RequestBody @Validated(UpdateGroup.class) SysPermissionDTO permissionDTO) {
        log.info("修改权限信息，权限ID：{}，权限信息：{}", id, permissionDTO);
        sysPermissionService.updatePermission(id, permissionDTO);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:permission:delete')")
    @Operation(summary = "删除权限", description = "逻辑删除权限")
    public Result<Void> deletePermission(
            @PathVariable @Parameter(description = "权限ID") Long id) {
        log.info("删除权限，权限ID：{}", id);
        sysPermissionService.deletePermission(id);
        return Result.success();
    }
}