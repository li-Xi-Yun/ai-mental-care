package org.lixiyun.server.controller.admin.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.admin.config.AiNodePromptDTO;
import org.lixiyun.pojo.vo.admin.config.AiNodePromptVO;
import org.lixiyun.server.service.admin.AdminAiNodePromptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员AI节点提示词相关接口
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai-node-prompt")
@Tag(name = "管理员AI节点提示词相关接口", description = "管理员AI节点提示词相关接口")
public class AdminAiNodePromptController {

    private final AdminAiNodePromptService adminAiNodePromptService;

    @GetMapping("/{id}")
//    @PreAuthorize("hasAuthority('ai:node:config:list')")
    @Operation(summary = "获取AI节点提示词", description = "单独获取节点的系统提示词，用于提示词编辑器加载")
    public Result<AiNodePromptVO> getPrompt(
            @PathVariable @NotNull @Parameter(description = "节点配置ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("获取AI节点提示词，id：{}", id);
        AiNodePromptVO vo = adminAiNodePromptService.getPrompt(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
//    @PreAuthorize("hasAuthority('ai:node:config:update')")
    @Operation(summary = "修改AI节点提示词", description = "单独修改节点的系统提示词，需传version乐观锁校验，自动记录变更历史")
    public Result<Void> updatePrompt(
            @PathVariable @NotNull @Parameter(description = "节点配置ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestBody @Validated AiNodePromptDTO dto) {
        log.info("修改AI节点提示词，id：{}", id);
        adminAiNodePromptService.updatePrompt(id, dto);
        return Result.success();
    }
}