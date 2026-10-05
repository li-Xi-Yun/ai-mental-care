package org.lixiyun.server.controller.admin.permission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.permission.SysRoleDTO;
import org.lixiyun.pojo.dto.admin.permission.SysRoleQueryDTO;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionTreeVO;
import org.lixiyun.pojo.vo.admin.permission.SysRoleDetailVO;
import org.lixiyun.pojo.vo.admin.permission.SysRolePageVO;
import org.lixiyun.pojo.vo.admin.permission.SysRoleSimpleVO;
import org.lixiyun.server.service.admin.SysRoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统角色管理控制器
 * <p>
 * 提供角色的增删改查、状态管理、权限分配等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31 11:39
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/sysRole")
@Tag(name = "系统角色管理接口", description = "系统角色管理接口")
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('admin:sysRole:page')")
    @Operation(summary = "分页查询角色列表", description = "分页查询角色列表，支持角色名称模糊匹配和状态筛选")
    public Result<PageResult<SysRolePageVO>> pageRoleList(@RequestBody @Validated SysRoleQueryDTO queryDTO) {
        log.info("分页查询角色列表：{}", queryDTO);
        PageResult<SysRolePageVO> pageResult = sysRoleService.pageRoleList(queryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:sysRole:detail')")
    @Operation(summary = "查询单个角色详情", description = "根据角色ID查询角色详细信息")
    public Result<SysRoleDetailVO> getRoleDetail(
            @PathVariable @Parameter(description = "角色ID") Long id) {
        log.info("查询角色详情，角色ID：{}", id);
        SysRoleDetailVO roleDetail = sysRoleService.getRoleDetail(id);
        return Result.success(roleDetail);
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('admin:sysRole:add')")
    @Operation(summary = "新增角色", description = "新增角色信息，角色标识必须唯一")
    public Result<Void> addRole(@RequestBody @Validated(AddGroup.class) SysRoleDTO roleDTO) {
        log.info("新增角色：{}", roleDTO);
        sysRoleService.addRole(roleDTO);
        return Result.success();
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('admin:sysRole:update')")
    @Operation(summary = "修改角色信息", description = "修改角色名称、备注等信息")
    public Result<Void> updateRole(
            @PathVariable @Parameter(description = "角色ID") Long id,
            @RequestBody @Validated(UpdateGroup.class) SysRoleDTO roleDTO) {
        log.info("修改角色信息，角色ID：{}，角色信息：{}", id, roleDTO);
        sysRoleService.updateRole(id, roleDTO);
        return Result.success();
    }

    @PutMapping("/status/{id}")
    @PreAuthorize("hasAuthority('admin:sysRole:status')")
    @Operation(summary = "修改角色状态", description = "启用或停用角色，status：0=正常，1=停用")
    public Result<Void> updateRoleStatus(
            @PathVariable @Parameter(description = "角色ID") Long id,
            @RequestParam @Parameter(description = "角色状态：0=正常，1=停用") @NumberOfRanges(min = 0, max = 1) Integer status) {
        log.info("修改角色状态，角色ID：{}，状态：{}", id, status);
        sysRoleService.updateRoleStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:sysRole:delete')")
    @Operation(summary = "删除角色", description = "逻辑删除角色，将status设置为2")
    public Result<Void> deleteRole(
            @PathVariable @Parameter(description = "角色ID") Long id) {
        log.info("删除角色，角色ID：{}", id);
        sysRoleService.deleteRole(id);
        return Result.success();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('admin:sysRole:permissions')")
    @Operation(summary = "查询角色绑定的权限集合", description = "根据角色ID查询该角色绑定的权限集合")
    public Result<List<SysPermissionTreeVO>> getRolePermissions(
            @PathVariable @Parameter(description = "角色ID") Long id) {
        log.info("查询角色权限ID集合，角色ID：{}", id);
        List<SysPermissionTreeVO> result = sysRoleService.getRolePermissions(id);
        return Result.success(result);
    }

    @PutMapping("/{id}/assignPermissions")
    @PreAuthorize("hasAuthority('admin:sysRole:assignPermissions')")
    @Operation(summary = "角色分配权限", description = "为角色分配权限，先删后增")
    public Result<Void> assignPermissions(
            @PathVariable @Parameter(description = "角色ID") Long id,
            @RequestParam @Parameter(description = "权限ID列表") @NotEmpty(message = "权限ID列表不能为空") List<Long> permissionIds) {
        log.info("角色分配权限，角色ID：{}，权限ID列表：{}", id, permissionIds);
        sysRoleService.assignPermissions(id, permissionIds);
        return Result.success();
    }

    @GetMapping("/allSimple")
    @PreAuthorize("hasAuthority('admin:sysRole:allSimple')")
    @Operation(summary = "获取全部正常角色列表", description = "获取全部正常角色id-name，用于下拉选择框")
    public Result<List<SysRoleSimpleVO>> getAllSimpleRoles() {
        log.info("获取全部正常角色列表");
        List<SysRoleSimpleVO> roles = sysRoleService.getAllSimpleRoles();
        return Result.success(roles);
    }
}