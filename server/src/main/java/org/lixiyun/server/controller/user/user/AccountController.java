package org.lixiyun.server.controller.user.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.UserGroup;
import org.lixiyun.pojo.dto.user.user.LoginUserDTO;
import org.lixiyun.pojo.dto.user.user.PasswordDTO;
import org.lixiyun.pojo.dto.user.user.RegisterUserDTO;
import org.lixiyun.pojo.vo.user.user.LoginResultVO;
import org.lixiyun.server.service.user.UserAccountService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/account")
@Tag(name = "用户账号相关接口", description = "用户账号相关接口")
public class AccountController {

    private final UserAccountService userAccountService;

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "用户通过普通验证码验证并进行注册")
    public Result<?> register(@RequestBody @Validated RegisterUserDTO registerUser){
        log.info("用户注册：{}", registerUser);
        userAccountService.register(registerUser);
        return Result.success();
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录接口")
    public Result<LoginResultVO> login(@RequestBody @Validated LoginUserDTO user) {
        log.info("用户登录:{}", user);
        LoginResultVO result = userAccountService.login(user);
        return Result.success(result);
    }

    @PostMapping("/logout")
//    @PreAuthorize("isAuthenticated()") // 要求用户已认证
    @Operation(summary = "用户登出", description = "用户登出")
    public Result<?> logout() {
        log.info("用户登出");
        userAccountService.logout();
        return Result.success();
    }

    @PatchMapping
    @Operation(summary = "用户密码修改", description = "用户密码修改")
    public Result<?> pwdUpdate(@RequestBody @Validated(UserGroup.PwdUpdate.class) PasswordDTO passwordDTO){
        log.info("密码修改：{}", passwordDTO);
        userAccountService.pwdUpdate(passwordDTO);
        return Result.success();
    }

}
