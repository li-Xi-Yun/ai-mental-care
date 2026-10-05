package org.lixiyun.server.controller.admin.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.admin.sysuser.SysUserQueryDTO;
import org.lixiyun.pojo.dto.admin.sysuser.SysUserStatusUpdateDTO;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;
import org.lixiyun.pojo.vo.admin.sysadmin.RoleVO;
import org.lixiyun.pojo.vo.admin.sysuser.SysUserDetailVO;
import org.lixiyun.pojo.vo.admin.sysuser.SysUserPageVO;
import org.lixiyun.server.service.admin.SysUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员用户管理相关接口
 * <p>管理普通用户信息</p>
 *
 * @author lixiyun
 * @since 2026-04-20 14:38
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/sysUser")
@Tag(name = "管理员用户管理相关接口", description = "管理员用户管理相关接口")
public class SysUserController {

    private final SysUserService sysUserService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('admin:sysUser:page')")
    @Operation(summary = "分页查询用户列表", description = "分页查询用户列表，支持用户名模糊匹配和状态筛选")
    public Result<PageResult<SysUserPageVO>> pageUserList(@RequestBody @Validated SysUserQueryDTO queryDTO) {
        log.info("分页查询用户列表：{}", queryDTO);
        PageResult<SysUserPageVO> pageResult = sysUserService.pageUserList(queryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:sysUser:detail')")
    @Operation(summary = "查询单个用户详情", description = "根据用户ID查询用户详细信息")
    public Result<SysUserDetailVO> getUserDetail(
            @PathVariable @Parameter(description = "用户ID") Long id) {
        log.info("查询用户详情，用户ID：{}", id);
        SysUserDetailVO userDetail = sysUserService.getUserDetail(id);
        return Result.success(userDetail);
    }

    @PutMapping("/status/{id}")
    @PreAuthorize("hasAuthority('admin:sysUser:status')")
    @Operation(summary = "修改用户账号状态", description = "启用或封禁用户账号，封禁时需填写封禁时间和理由")
    public Result<Void> updateUserStatus(
            @PathVariable @Parameter(description = "用户ID") Long id,
            @RequestBody @Validated SysUserStatusUpdateDTO statusDTO) {
        log.info("修改用户状态，用户ID：{}，状态信息：{}", id, statusDTO);
        sysUserService.updateUserStatus(id, statusDTO);
        return Result.success();
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('admin:sysUser:roles')")
    @Operation(summary = "查询用户已分配的角色列表", description = "根据用户ID查询其已分配的角色信息")
    public Result<List<RoleVO>> getUserRoles(
            @PathVariable @Parameter(description = "用户ID") Long id) {
        log.info("查询用户角色列表，用户ID：{}", id);
        List<RoleVO> roles = sysUserService.getUserRoles(id);
        return Result.success(roles);
    }

    @PutMapping("/{id}/assignRoles")
    @PreAuthorize("hasAuthority('admin:sysUser:assignRoles')")
    @Operation(summary = "给用户分配角色", description = "为用户分配角色，全量覆盖（先删后插）")
    public Result<Void> assignRolesToUser(
            @PathVariable @Parameter(description = "用户ID") Long id,
            @RequestParam @Parameter(description = "角色ID列表") @NotEmpty(message = "角色ID列表不能为空") List<Long> roleIdList) {
        log.info("给用户分配角色，用户ID：{}，角色ID列表：{}", id, roleIdList);
        sysUserService.assignRolesToUser(id, roleIdList);
        return Result.success();
    }

    @GetMapping("/{id}/tempPerms")
    @PreAuthorize("hasAuthority('admin:sysUser:tempPerms')")
    @Operation(summary = "查询用户临时权限记录", description = "查询该用户全部临时权限记录，包括临时权限、到期时间、授予原因")
    public Result<List<TempPermissionVO>> getUserTempPermissions(
            @PathVariable @Parameter(description = "用户ID") Long id,
            @RequestParam(required = false) @Parameter(description = "状态, 0=有效，1=手动作废，2=已过期，不传默认查询所有状态") @NumberOfRanges(max = 2) Integer status) {
        log.info("查询用户临时权限记录，用户ID：{}，状态：{}", id, status);
        List<TempPermissionVO> tempPermissions = sysUserService.getUserTempPermissions(id, status);
        return Result.success(tempPermissions);
    }
}