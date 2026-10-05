package org.lixiyun.server.controller.admin.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.user.AdminUserCreateDTO;
import org.lixiyun.pojo.dto.admin.user.AdminUserUpdateDTO;
import org.lixiyun.pojo.dto.admin.user.ResetPasswordDTO;
import org.lixiyun.pojo.dto.admin.user.UserQueryDTO;
import org.lixiyun.pojo.dto.admin.user.UserStatusDTO;
import org.lixiyun.pojo.vo.admin.user.UserVO;
import org.lixiyun.server.service.admin.AdminUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
@RequestMapping("/admin/user")
@Tag(name = "管理员用户管理相关接口", description = "管理员用户管理相关接口")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/list")
    @PreAuthorize("hasAuthority('admin:user:list')")
    @Operation(summary = "查询所有用户", description = "分页查询所有用户信息，支持账号名/用户名/手机号模糊匹配和账号状态筛选")
    public Result<PageResult<UserVO>> listUsers(@RequestBody @Validated UserQueryDTO queryDTO) {
        log.info("查询所有用户：{}", queryDTO);
        PageResult<UserVO> result = adminUserService.listUsers(queryDTO);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:user:list')")
    @Operation(summary = "获取用户详情", description = "根据用户ID获取用户详细信息，用于编辑弹窗回显")
    public Result<UserVO> getUserDetail(
            @PathVariable @NotNull @Parameter(description = "用户ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("获取用户详情，用户id：{}", id);
        UserVO vo = adminUserService.getUserDetail(id);
        return Result.success(vo);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('admin:user:create')")
    @Operation(summary = "新增用户", description = "管理端新增普通用户并绑定普通用户角色")
    public Result<Void> createUser(@RequestBody @Validated AdminUserCreateDTO createDTO) {
        log.info("新增用户：{}", createDTO);
        adminUserService.createUser(createDTO);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:user:update')")
    @Operation(summary = "编辑用户", description = "管理端编辑用户基本信息，支持账号名变更")
    public Result<Void> updateUser(
            @PathVariable @NotNull @Parameter(description = "用户ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestBody @Validated AdminUserUpdateDTO updateDTO) {
        log.info("编辑用户，用户id：{}，dto：{}", id, updateDTO);
        adminUserService.updateUser(id, updateDTO);
        return Result.success();
    }

    @PatchMapping("/status")
    @PreAuthorize("hasAuthority('admin:user:update')")
    @Operation(summary = "用户状态修改", description = "修改用户账号状态，支持封禁并填写封禁理由")
    public Result<Void> updateUserStatus(@RequestBody @Validated UserStatusDTO statusDTO) {
        log.info("用户状态修改：{}", statusDTO);
        adminUserService.updateUserStatus(statusDTO);
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('admin:user:update')")
    @Operation(summary = "重置用户密码", description = "由管理员为用户设置新密码")
    public Result<Void> resetPassword(
            @PathVariable @NotNull @Parameter(description = "用户ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestBody @Validated ResetPasswordDTO resetPasswordDTO) {
        log.info("重置用户密码，用户id：{}", id);
        adminUserService.resetPassword(id, resetPasswordDTO);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:user:delete')")
    @Operation(summary = "用户注销", description = "将用户账号状态置为注销(status=3)")
    public Result<Void> deleteUser(
            @PathVariable @NotNull @Parameter(description = "用户ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("用户注销，用户id：{}", id);
        adminUserService.deleteUser(id);
        return Result.success();
    }

}