package org.lixiyun.server.controller.user.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.base.PageBaseDTO;
import org.lixiyun.pojo.dto.user.conversation.ConversationInfoDTO;
import org.lixiyun.pojo.vo.user.conversation.ConversationVO;
import org.lixiyun.server.service.user.ConversationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lixiyun
 * @since 2026-03-19 20:21
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/conversation")
@Tag(name = "会话相关接口", description = "会话相关接口")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/list")
    @Operation(summary = "会话列表分页展示", description = "按更新时间逆序排列")
    public Result<PageResult<ConversationVO>> listDisplay(
            @RequestBody @Validated PageBaseDTO pageBaseDTO
    ) {
        log.info("分页查询会话列表: {}, {}", pageBaseDTO.getPageNum(), pageBaseDTO.getPageSize());
        PageResult<ConversationVO> result = conversationService.listDisplay(pageBaseDTO.getPageNum(), pageBaseDTO.getPageSize());
        return Result.success(result);
    }

    @PutMapping("/{conversationId}/name")
    @Operation(summary = "修改会话属性", description = "修改指定会话的元数据信息")
    public Result<Void> updateConversationInfo(
            @PathVariable @Parameter(description = "会话ID", required = true) @NotNull Long conversationId,
            @RequestBody @Valid ConversationInfoDTO conversationInfoDTO
    ) {
        log.info("修改会话属性:{}, {}", conversationId, conversationInfoDTO);
        conversationService.updateConversationInfo(conversationId, conversationInfoDTO);
        return Result.success();
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "删除会话", description = "删除指定会话及其关联数据")
    public Result<Void> deleteConversation(@PathVariable @Parameter(description = "会话ID", required = true) @NotNull Long conversationId) {
        log.info("删除会话:{}", conversationId);
        conversationService.deleteConversation(conversationId);
        return Result.success();
    }

}