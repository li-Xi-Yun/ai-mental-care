package org.lixiyun.server.controller.user.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.base.PageBaseDTO;
import org.lixiyun.pojo.vo.user.conversation.ConversationRoundVO;
import org.lixiyun.server.service.user.DialogueService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lixiyun
 * @since 2026-03-19 20:24
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/conversation/dialogue")
@Tag(name = "对话信息相关接口", description = "对话内容上下文信息相关接口")
public class DialogueController {

    private final DialogueService dialogueService;

    @PostMapping(value = "/{conversationId}/memory")
    @Operation(summary = "对话记录分页展示", description = "按轮次聚合返回：每条记录为一个轮次（含该轮全部消息与关联的待处理人工交互卡片），轮次按创建时间逆序排列")
    public Result<PageResult<ConversationRoundVO>> listMemory(
            @PathVariable @Parameter(description = "会话ID") Long conversationId,
            @RequestBody @Validated PageBaseDTO pageBaseDTO
    ) {
        log.info("分页查询对话记录: {}, {}, {}", pageBaseDTO.getPageNum(), pageBaseDTO.getPageSize(), conversationId);
        PageResult<ConversationRoundVO> result = dialogueService.listMemory(conversationId, pageBaseDTO.getPageNum(), pageBaseDTO.getPageSize());
        return Result.success(result);
    }

}