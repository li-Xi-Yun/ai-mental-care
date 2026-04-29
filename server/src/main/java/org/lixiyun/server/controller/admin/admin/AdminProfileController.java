package org.lixiyun.server.controller.admin.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.admin.profile.AdminProfileDTO;
import org.lixiyun.pojo.vo.admin.profile.AdminProfileVO;
import org.lixiyun.server.service.admin.AdminProfileService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/profile")
@Tag(name = "用户个人资料相关接口", description = "用户个人资料相关接口")
public class AdminProfileController {

    private final AdminProfileService adminProfileService;

    @PutMapping("/profileUpdate")
    @Operation(summary = "更新管理员个人资料", description = "更新管理员个人资料")
    public Result<?> updateProfile(@RequestBody @Validated @Parameter(description = "管理员个人资料信息") AdminProfileDTO adminProfileDTO){
        log.info("更新管理员个人资料：{}", adminProfileDTO);
        adminProfileService.updateProfile(adminProfileDTO);
        return Result.success();
    }

    @GetMapping("/profile")
    @Operation(summary = "获取管理员个人资料", description = "根据管理员ID获取管理员个人资料信息")
    public Result<AdminProfileVO> getAdminProfile() {
        log.info("获取管理员个人资料");
        AdminProfileVO userProfile = adminProfileService.getAdminProfile();
        return Result.success(userProfile);
    }

}
