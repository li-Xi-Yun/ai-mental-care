package org.lixiyun.server.controller.admin.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.UserGroup;
import org.lixiyun.pojo.dto.admin.admin.LoginAdminDTO;
import org.lixiyun.pojo.dto.user.user.PasswordDTO;
import org.lixiyun.pojo.vo.admin.admin.AdminLoginResultVO;
import org.lixiyun.server.service.admin.AdminAccountService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/account")
@Tag(name = "管理员账号相关接口", description = "管理员账号相关接口（登录鉴权）")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @PostMapping("/login")
    @Operation(summary = "管理员登录接口", description = "管理员通过账号名登录")
    public Result<AdminLoginResultVO> login(@RequestBody @Validated LoginAdminDTO loginAdminDTO) {
        log.info("管理员登录:{}", loginAdminDTO);
        AdminLoginResultVO result = adminAccountService.login(loginAdminDTO);
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