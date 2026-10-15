package org.lixiyun.server.controller.admin.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.admin.quota.QuotaUserAdjustDTO;
import org.lixiyun.pojo.vo.admin.quota.QuotaUserVO;
import org.lixiyun.server.service.admin.SysUserQuotaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员 AI 对话配额用户调整相关接口
 * <p>支持管理员对指定用户某资源的个性化上限 / 已用次数 / 不限量豁免进行调整</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/quota/users")
@Tag(name = "管理员AI对话配额用户调整接口", description = "管理员AI对话配额用户调整接口")
public class SysUserQuotaController {

    private final SysUserQuotaService sysUserQuotaService;

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('quota:user:list')")
    @Operation(summary = "查询指定用户全部资源配额", description = "按全局配置基底合并用户配额行，返回各资源使用量/剩余")
    public Result<List<QuotaUserVO>> listUserQuota(
            @PathVariable @NotNull @Parameter(description = "用户ID", required = true, in = ParameterIn.PATH) Long userId) {
        log.info("查询用户AI配额列表，userId：{}", userId);
        return Result.success(sysUserQuotaService.listUserQuota(userId));
    }

    @PutMapping("/{userId}/resources/{resourceType}")
    @PreAuthorize("hasAuthority('quota:user:adjust')")
    @Operation(summary = "调整用户某资源配额", description = "修改个性化上限 / 已用次数 / 不限量豁免状态；传 NULL 表示清除个性化/不修改豁免")
    public Result<Void> adjustUserQuota(
            @PathVariable @NotNull @Parameter(description = "用户ID", required = true, in = ParameterIn.PATH) Long userId,
            @PathVariable @NotNull @Parameter(description = "资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D", required = true, in = ParameterIn.PATH) Integer resourceType,
            @RequestBody @Validated QuotaUserAdjustDTO dto) {
        log.info("调整用户AI配额，userId：{}，resourceType：{}，dto：{}", userId, resourceType, dto);
        sysUserQuotaService.updateUserQuota(userId, resourceType, dto);
        return Result.success();
    }
}