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
import org.lixiyun.pojo.dto.admin.sysadmin.AdminAddDTO;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminQueryDTO;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminStatusUpdateDTO;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminUpdateDTO;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;
import org.lixiyun.pojo.vo.admin.sysadmin.AdminDetailVO;
import org.lixiyun.pojo.vo.admin.sysadmin.AdminPageVO;
import org.lixiyun.pojo.vo.admin.sysadmin.RoleVO;
import org.lixiyun.server.service.admin.SysAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员管理相关接口
 * <p>管理员新增、列表、详情、编辑（含角色调整）、状态修改、注销，全部为超级管理员专属操作</p>
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/sysAdmin")
@Tag(name = "管理员管理相关接口", description = "管理员管理相关接口（超级管理员专属）")
public class SysAdminController {

    private final SysAdminService sysAdminService;

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('admin:sysAdmin:page')")
    @Operation(summary = "分页查询管理员列表", description = "分页查询管理员列表，支持用户名模糊匹配和状态筛选")
    public Result<PageResult<AdminPageVO>> pageAdminList(@RequestBody @Validated AdminQueryDTO queryDTO) {
        log.info("分页查询管理员列表：{}", queryDTO);
        PageResult<AdminPageVO> pageResult = sysAdminService.pageAdminList(queryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:sysAdmin:detail')")
    @Operation(summary = "查询单个管理员详情", description = "根据管理员ID查询管理员详细信息")
    public Result<AdminDetailVO> getAdminDetail(
            @PathVariable @Parameter(description = "管理员ID") Long id) {
        log.info("查询管理员详情，管理员ID：{}", id);
        AdminDetailVO adminDetail = sysAdminService.getAdminDetail(id);
        return Result.success(adminDetail);
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('admin:sysAdmin:add')")
    @Operation(summary = "新增管理员账号", description = "新增管理员账号，密码加密入库")
    public Result<Void> addAdmin(@RequestBody @Validated AdminAddDTO adminAddDTO) {
        log.info("新增管理员：{}", adminAddDTO);
        sysAdminService.addAdmin(adminAddDTO);
        return Result.success();
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('admin:sysAdmin:update')")
    @Operation(summary = "修改管理员基础信息", description = "修改管理员的用户名、邮箱、手机等基础信息")
    public Result<Void> updateAdmin(@RequestBody @Validated AdminUpdateDTO adminUpdateDTO) {
        log.info("修改管理员信息：{}", adminUpdateDTO);
        sysAdminService.updateAdmin(adminUpdateDTO);
        return Result.success();
    }

    @PutMapping("/status/{id}")
    @PreAuthorize("hasAuthority('admin:sysAdmin:status')")
    @Operation(summary = "修改管理员账号状态", description = "启用或封禁管理员账号，封禁时需填写封禁时间和理由")
    public Result<Void> updateAdminStatus(
            @PathVariable @Parameter(description = "管理员ID") Long id,
            @RequestBody @Validated AdminStatusUpdateDTO statusDTO) {
        log.info("修改管理员状态，管理员ID：{}，状态信息：{}", id, statusDTO);
        sysAdminService.updateAdminStatus(id, statusDTO);
        return Result.success();
    }

    @PutMapping("/resetPwd/{id}")
    @PreAuthorize("hasAuthority('admin:sysAdmin:resetPwd')")
    @Operation(summary = "重置管理员密码", description = "重置指定管理员的密码为默认密码")
    public Result<Void> resetAdminPassword(
            @PathVariable @Parameter(description = "管理员ID") Long id) {
        log.info("重置管理员密码，管理员ID：{}", id);
        sysAdminService.resetAdminPassword(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:sysAdmin:delete')")
    @Operation(summary = "管理员注销", description = "将管理员账号状态置为注销（status=3），不能注销自己")
    public Result<Void> deleteAdmin(
            @PathVariable @Parameter(description = "管理员ID") Long id) {
        log.info("删除管理员，管理员ID：{}", id);
        sysAdminService.deleteAdmin(id);
        return Result.success();
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('admin:sysAdmin:roles')")
    @Operation(summary = "查询管理员已分配的角色列表", description = "根据管理员ID查询其已分配的角色信息")
    public Result<List<RoleVO>> getAdminRoles(
            @PathVariable @Parameter(description = "管理员ID") Long id) {
        log.info("查询管理员角色列表，管理员ID：{}", id);
        List<RoleVO> roles = sysAdminService.getAdminRoles(id);
        return Result.success(roles);
    }

    @PutMapping("/{id}/assignRoles")
    @PreAuthorize("hasAuthority('admin:sysAdmin:assignRoles')")
    @Operation(summary = "给管理员分配角色", description = "为管理员分配角色，全量覆盖（先删后插）")
    public Result<Void> assignRolesToAdmin(
            @PathVariable @Parameter(description = "管理员ID") Long id,
            @RequestBody @NotEmpty(message = "角色ID列表不能为空") @Parameter(description = "角色ID列表") List<Long> roleIdList) {
        log.info("给管理员分配角色，管理员ID：{}，角色ID列表：{}", id, roleIdList);
        sysAdminService.assignRolesToAdmin(id, roleIdList);
        return Result.success();
    }

    @GetMapping("/{id}/tempPerms")
    @PreAuthorize("hasAuthority('admin:sysAdmin:tempPerms')")
    @Operation(summary = "查询管理员临时权限记录", description = "查询该管理员全部临时权限记录，包括临时权限、到期时间、授予原因")
    public Result<List<TempPermissionVO>> getAdminTempPermissions(
            @PathVariable @Parameter(description = "管理员ID") Long id,
            @RequestParam(required = false) @Parameter(description = "状态, 0=有效，1=手动作废，2=已过期") @NumberOfRanges(max = 2) Integer status) {
        log.info("查询管理员临时权限记录，管理员ID：{}，状态：{}", id, status);
        List<TempPermissionVO> tempPermissions = sysAdminService.getAdminTempPermissions(id, status);
        return Result.success(tempPermissions);
    }
}