package org.lixiyun.server.controller.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.vo.conversation.ConversationMemoryVO;
import org.lixiyun.server.service.DialogueService;
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
@RequestMapping("/conversaion/dialogue")
@Tag(name = "对话信息相关接口", description = "对话内容上下文信息相关接口")
public class DialogueController {

    private final DialogueService dialogueService;


    @GetMapping(value = "/{conversationId}/memory")
    @Operation(summary = "对话记录分页展示", description = "按创建时间正序排列，一共有三个类型，user、thinking、assistant，如果轮次相同，表明为同一个对话内容")
    public Result<PageResult<ConversationMemoryVO>> listMemory(
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize,
            @PathVariable @Parameter(description = "会话ID") Long conversationId
    ) {
        log.info("分页查询对话记录: {}, {}, {}", pageNum, pageSize, conversationId);
        PageResult<ConversationMemoryVO> result = dialogueService.listMemory(conversationId, pageNum, pageSize);
        return Result.success(result);
    }

    @DeleteMapping("/memory/{conversationId}")
    @Operation(summary = "删除对话记录", description = "删除指定对话记录，调用该接口，对应会话的轮次在数据库中会减减少，前端需要重新获取会话的元数据信息")
    public Result<Void> deleteConversationMemory(
            @PathVariable @NotNull @Parameter(description = "会话ID") Long conversationId,
            @RequestParam @NotNull @Parameter(description = "该对话的轮次") Integer roundNum
    ) {
        log.info("删除对话记录: {}, {}", conversationId, roundNum);
        dialogueService.deleteConversationMemory(conversationId, roundNum);
        return Result.success();
    }

}
