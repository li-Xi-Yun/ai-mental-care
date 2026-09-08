package org.lixiyun.server.controller.admin.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.config.AiNodeHistoryQueryDTO;
import org.lixiyun.pojo.vo.admin.config.AiNodeConfigHistoryVO;
import org.lixiyun.server.service.admin.AdminAiNodeHistoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员AI节点配置变更历史相关接口
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai-node-history")
@Tag(name = "管理员AI节点配置变更历史相关接口", description = "管理员AI节点配置变更历史相关接口")
public class AdminAiNodeHistoryController {

    private final AdminAiNodeHistoryService adminAiNodeHistoryService;

    @PostMapping("/{id}/page")
//    @PreAuthorize("hasAuthority('ai:node:config:list')")
    @Operation(summary = "分页查询AI节点配置变更历史", description = "按时间倒序查询某节点的配置变更记录，支持变更摘要模糊匹配")
    public Result<PageResult<AiNodeConfigHistoryVO>> pageHistory(
            @PathVariable @NotNull @Parameter(description = "节点配置ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestBody @Validated AiNodeHistoryQueryDTO queryDTO) {
        log.info("分页查询AI节点配置变更历史，id：{}，queryDTO：{}", id, queryDTO);
        PageResult<AiNodeConfigHistoryVO> result = adminAiNodeHistoryService.pageHistory(id, queryDTO);
        return Result.success(result);
    }

    @GetMapping("/detail/{historyId}")
//    @PreAuthorize("hasAuthority('ai:node:config:list')")
    @Operation(summary = "获取变更历史详情", description = "查询单条变更记录的完整快照（含old/new对比）")
    public Result<AiNodeConfigHistoryVO> getHistoryDetail(
            @PathVariable @NotNull @Parameter(description = "历史记录ID", required = true, in = ParameterIn.PATH) Long historyId) {
        log.info("获取变更历史详情，historyId：{}", historyId);
        AiNodeConfigHistoryVO vo = adminAiNodeHistoryService.getHistoryDetail(historyId);
        return Result.success(vo);
    }

    @PostMapping("/{id}/rollback")
//    @PreAuthorize("hasAuthority('ai:node:config:update')")
    @Operation(summary = "回滚到指定历史版本", description = "将节点配置回滚到指定历史版本，自动记录回滚历史并刷新缓存")
    public Result<Void> rollback(
            @PathVariable @NotNull @Parameter(description = "节点配置ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestParam @NotNull @Parameter(description = "目标历史记录ID", required = true) Long historyId) {
        log.info("回滚AI节点配置，id：{}，historyId：{}", id, historyId);
        adminAiNodeHistoryService.rollback(id, historyId);
        return Result.success();
    }
}