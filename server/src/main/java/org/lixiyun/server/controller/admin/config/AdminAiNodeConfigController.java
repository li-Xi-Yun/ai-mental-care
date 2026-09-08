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
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.config.AdminAiNodeConfigQueryDTO;
import org.lixiyun.pojo.dto.admin.config.AiNodeConfigBatchEnabledDTO;
import org.lixiyun.pojo.dto.admin.config.AiNodeConfigDTO;
import org.lixiyun.pojo.vo.admin.config.AiNodeConfigSimpleVO;
import org.lixiyun.pojo.vo.admin.config.AiNodeConfigVO;
import org.lixiyun.server.service.admin.AdminAiNodeConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员AI节点配置相关接口
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai-node-config")
@Tag(name = "管理员AI节点配置相关接口", description = "管理员AI节点配置相关接口")
public class AdminAiNodeConfigController {

    private final AdminAiNodeConfigService adminAiNodeConfigService;

    @PostMapping("/page")
//    @PreAuthorize("hasAuthority('ai:node:config:list')")
    @Operation(summary = "分页查询AI节点配置", description = "支持节点标识/名称模糊匹配、分组筛选、模型类型筛选、启用状态筛选")
    public Result<PageResult<AiNodeConfigSimpleVO>> pageAiNodeConfig(@RequestBody @Validated AdminAiNodeConfigQueryDTO queryDTO) {
        log.info("分页查询AI节点配置：{}", queryDTO);
        PageResult<AiNodeConfigSimpleVO> result = adminAiNodeConfigService.pageAiNodeConfig(queryDTO);
        return Result.success(result);
    }

    @GetMapping("/{id}")
//    @PreAuthorize("hasAuthority('ai:node:config:list')")
    @Operation(summary = "获取AI节点配置详情", description = "根据主键ID获取完整配置（含提示词全文）")
    public Result<AiNodeConfigVO> getAiNodeConfigDetail(
            @PathVariable @NotNull @Parameter(description = "节点配置ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("获取AI节点配置详情，id：{}", id);
        AiNodeConfigVO vo = adminAiNodeConfigService.getAiNodeConfigDetail(id);
        return Result.success(vo);
    }

    @PostMapping
//    @PreAuthorize("hasAuthority('ai:node:config:create')")
    @Operation(summary = "新增AI节点配置", description = "新增一个诊断节点的配置信息")
    public Result<Void> createAiNodeConfig(@RequestBody @Validated(AddGroup.class) AiNodeConfigDTO dto) {
        log.info("新增AI节点配置：{}", dto);
        adminAiNodeConfigService.createAiNodeConfig(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
//    @PreAuthorize("hasAuthority('ai:node:config:update')")
    @Operation(summary = "修改AI节点配置", description = "修改节点配置，需传version乐观锁校验")
    public Result<Void> updateAiNodeConfig(
            @PathVariable @NotNull @Parameter(description = "节点配置ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestBody @Validated(UpdateGroup.class) AiNodeConfigDTO dto) {
        log.info("修改AI节点配置，id：{}，dto：{}", id, dto);
        adminAiNodeConfigService.updateAiNodeConfig(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
//    @PreAuthorize("hasAuthority('ai:node:config:delete')")
    @Operation(summary = "删除AI节点配置", description = "根据主键ID逻辑删除配置")
    public Result<Void> deleteAiNodeConfig(
            @PathVariable @NotNull @Parameter(description = "节点配置ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("删除AI节点配置，id：{}", id);
        adminAiNodeConfigService.deleteAiNodeConfig(id);
        return Result.success();
    }

    @PutMapping("/batch-enabled")
//    @PreAuthorize("hasAuthority('ai:node:config:update')")
    @Operation(summary = "批量启用/禁用AI节点配置", description = "批量切换节点的启用状态")
    public Result<Void> batchUpdateEnabled(@RequestBody @Validated AiNodeConfigBatchEnabledDTO dto) {
        log.info("批量启用/禁用AI节点配置：{}", dto);
        adminAiNodeConfigService.batchUpdateEnabled(dto);
        return Result.success();
    }

    @GetMapping("/node-keys")
//    @PreAuthorize("hasAuthority('ai:node:config:list')")
    @Operation(summary = "获取全部节点唯一标识列表", description = "返回所有节点唯一标识，用于前端下拉选项")
    public Result<List<String>> listNodeKeys() {
        log.info("获取全部节点唯一标识列表");
        List<String> nodeKeys = adminAiNodeConfigService.listNodeKeys();
        return Result.success(nodeKeys);
    }
}