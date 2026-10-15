package org.lixiyun.server.controller.admin.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.admin.quota.QuotaConfigUpdateDTO;
import org.lixiyun.pojo.vo.admin.quota.QuotaConfigVO;
import org.lixiyun.server.service.admin.AdminQuotaConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员 AI 对话配额全局配置相关接口
 * <p>按资源类型维护全局默认限流配置（默认上限 / 可用性开关 / 限流开关 / 周期）</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/quota/config")
@Tag(name = "管理员AI对话配额全局配置接口", description = "管理员AI对话配额全局配置接口")
public class AdminQuotaController {

    private final AdminQuotaConfigService adminQuotaConfigService;

    @GetMapping
    @PreAuthorize("hasAuthority('quota:config:list')")
    @Operation(summary = "查询全部资源类型全局配置", description = "返回各资源类型默认上限、可用性状态、限流状态、周期与备注")
    public Result<List<QuotaConfigVO>> listConfig() {
        log.info("查询AI对话配额全局配置");
        List<QuotaConfigVO> result = adminQuotaConfigService.listConfig();
        return Result.success(result);
    }

    @PutMapping("/{resourceType}")
    @PreAuthorize("hasAuthority('quota:config:update')")
    @Operation(summary = "修改单个资源全局配置", description = "局部更新：默认上限 / 可用性 / 限流开关 / 周期；未传字段保持原值")
    public Result<Void> updateConfig(
            @PathVariable @NotNull @Parameter(description = "资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D", required = true, in = ParameterIn.PATH) Integer resourceType,
            @RequestBody @Validated QuotaConfigUpdateDTO dto) {
        log.info("修改AI对话配额全局配置，resourceType：{}，dto：{}", resourceType, dto);
        adminQuotaConfigService.updateConfig(resourceType, dto);
        return Result.success();
    }
}