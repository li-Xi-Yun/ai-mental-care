package org.lixiyun.server.controller.admin.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.UserGroup;
import org.lixiyun.pojo.dto.admin.admin.LoginAdminDTO;
import org.lixiyun.pojo.dto.admin.admin.RegisterAdminDTO;
import org.lixiyun.pojo.dto.user.user.PasswordDTO;
import org.lixiyun.pojo.vo.user.user.LoginResultVO;
import org.lixiyun.server.service.admin.AdminAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/account")
@Tag(name = "管理员账号相关接口", description = "管理员账号相关接口")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('admin:account:register')")
    @Operation(summary = "管理员注册", description = "管理员注册")
    public Result<?> register(@RequestBody @Validated RegisterAdminDTO registerAdminDTO){
        log.info("管理员注册：{}", registerAdminDTO);
        adminAccountService.register(registerAdminDTO);
        return Result.success();
    }

    @PostMapping("/login")
    @Operation(summary = "管理员登录接口", description = "管理员登录接口")
    public Result<LoginResultVO> login(@RequestBody @Validated LoginAdminDTO loginAdminDTO) {
        log.info("管理员登录:{}", loginAdminDTO);
        LoginResultVO result = adminAccountService.login(loginAdminDTO);
        return Result.success(result);
    }

    @PostMapping("/logout")
    @Operation(summary = "管理员登出", description = "管理员登出")
    public Result<?> logout() {
        log.info("用户登出");
        adminAccountService.logout();
        return Result.success();
    }

    @PatchMapping
    @Operation(summary = "管理员密码修改", description = "管理员密码修改")
    public Result<?> pwdUpdate(@RequestBody @Validated(UserGroup.PwdUpdate.class) PasswordDTO passwordDTO){
        log.info("密码修改：{}", passwordDTO);
        adminAccountService.pwdUpdate(passwordDTO);
        return Result.success();
    }

}
