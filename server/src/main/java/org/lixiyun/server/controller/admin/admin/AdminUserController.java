package org.lixiyun.server.controller.admin.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.user.AdminQueryDTO;
import org.lixiyun.pojo.dto.admin.user.AdminStatusDTO;
import org.lixiyun.pojo.dto.admin.user.UserQueryDTO;
import org.lixiyun.pojo.dto.admin.user.UserStatusDTO;
import org.lixiyun.pojo.vo.admin.user.AdminVO;
import org.lixiyun.pojo.vo.admin.user.UserVO;
import org.lixiyun.server.service.admin.AdminUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户管理相关接口
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
//    @PreAuthorize("hasAuthority('admin:user:list')")
    @Operation(summary = "查询所有用户", description = "分页查询所有用户信息，支持用户名模糊匹配和账号状态筛选")
    public Result<PageResult<UserVO>> listUsers(@RequestBody @Validated UserQueryDTO queryDTO) {
        log.info("查询所有用户：{}", queryDTO);
        PageResult<UserVO> result = adminUserService.listUsers(queryDTO);
        return Result.success(result);
    }

    @PostMapping("/admin/list")
//    @PreAuthorize("hasAuthority('admin:admin:list')")
    @Operation(summary = "查询所有普通管理员", description = "分页查询所有普通管理员信息，支持管理员名模糊匹配和账号状态筛选")
    public Result<PageResult<AdminVO>> listAdmins(@RequestBody @Validated AdminQueryDTO queryDTO) {
        log.info("查询所有普通管理员：{}", queryDTO);
        PageResult<AdminVO> result = adminUserService.listAdmins(queryDTO);
        return Result.success(result);
    }

    @PatchMapping("/status")
//    @PreAuthorize("hasAuthority('admin:user:status:update')")
    @Operation(summary = "用户状态修改", description = "修改用户账号状态，支持封禁并填写封禁理由")
    public Result<Void> updateUserStatus(@RequestBody @Validated UserStatusDTO statusDTO) {
        log.info("用户状态修改：{}", statusDTO);
        adminUserService.updateUserStatus(statusDTO);
        return Result.success();
    }

    @PatchMapping("/admin/status")
//    @PreAuthorize("hasAuthority('admin:admin:status:update')")
    @Operation(summary = "管理员状态修改", description = "修改管理员账号状态，支持封禁并填写封禁理由和封禁时间")
    public Result<Void> updateAdminStatus(@RequestBody @Validated AdminStatusDTO statusDTO) {
        log.info("管理员状态修改：{}", statusDTO);
        adminUserService.updateAdminStatus(statusDTO);
        return Result.success();
    }

}
