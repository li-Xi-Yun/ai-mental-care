package org.lixiyun.server.controller.user.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.user.UserProfileDTO;
import org.lixiyun.pojo.vo.user.user.UserProfileVO;
import org.lixiyun.server.service.user.ProfileService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/profile")
@Tag(name = "用户个人资料相关接口", description = "用户个人资料相关接口")
public class ProfileController {

    private final ProfileService profileService;

    @PutMapping("/profileUpdate")
    @Operation(summary = "更新用户个人资料", description = "更新用户个人资料信息")
    public Result<?> updateProfile(@RequestBody @Validated @Parameter(description = "用户个人资料信息") UserProfileDTO userProfileDTO){
        log.info("更新用户个人资料信息：{}", userProfileDTO);
        profileService.updateProfile(userProfileDTO);
        return Result.success();
    }

    @GetMapping("/profile")
    @Operation(summary = "获取用户个人资料", description = "根据用户ID获取用户个人资料信息")
    public Result<UserProfileVO> getUserProfile() {
        log.info("获取用户个人资料信息");
        UserProfileVO userProfile = profileService.getUserProfile();
        return Result.success(userProfile);
    }

}
