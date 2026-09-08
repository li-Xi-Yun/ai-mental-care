package org.lixiyun.server.controller.admin.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.vo.admin.config.AiNodeCacheVO;
import org.lixiyun.server.service.admin.AdminAiNodeCacheService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员AI节点配置缓存相关接口
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai-node-cache")
@Tag(name = "管理员AI节点配置缓存相关接口", description = "管理员AI节点配置缓存相关接口")
public class AdminAiNodeCacheController {

    private final AdminAiNodeCacheService adminAiNodeCacheService;

    @GetMapping("/status")
//    @PreAuthorize("hasAuthority('ai:node:config:list')")
    @Operation(summary = "获取缓存状态", description = "返回当前内存缓存中的节点数量和节点唯一标识列表")
    public Result<AiNodeCacheVO> getCacheStatus() {
        log.info("获取AI节点配置缓存状态");
        AiNodeCacheVO vo = adminAiNodeCacheService.getCacheStatus();
        return Result.success(vo);
    }

    @PostMapping("/refresh")
//    @PreAuthorize("hasAuthority('ai:node:config:update')")
    @Operation(summary = "全量刷新缓存", description = "清空并重新从数据库全量加载所有节点配置到内存缓存")
    public Result<Void> refreshAll() {
        log.info("全量刷新AI节点配置缓存");
        adminAiNodeCacheService.refreshAll();
        return Result.success();
    }

    @PostMapping("/refresh/{id}")
//    @PreAuthorize("hasAuthority('ai:node:config:update')")
    @Operation(summary = "刷新单个节点缓存", description = "从数据库重新加载指定节点的配置到内存缓存")
    public Result<Void> refresh(
            @PathVariable @NotNull @Parameter(description = "节点配置ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("刷新单个AI节点配置缓存，id：{}", id);
        adminAiNodeCacheService.refresh(id);
        return Result.success();
    }
}