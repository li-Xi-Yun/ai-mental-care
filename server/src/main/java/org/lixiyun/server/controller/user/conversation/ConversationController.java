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
import org.lixiyun.common.validation.annotation.NumberOfRanges;
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
@RequestMapping("/conversation")
@Tag(name = "会话相关接口", description = "会话相关接口")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping("/list")
    @Operation(summary = "会话列表分页展示", description = "按更新时间逆序排列")
    public Result<PageResult<ConversationVO>> listDisplay(
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize
    ) {
        log.info("分页查询会话列表: {}, {}", pageNum, pageSize);
        PageResult<ConversationVO> result = conversationService.listDisplay(pageNum, pageSize);
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

    @PutMapping("/{conversationId}/initialize-name")
    @Operation(summary = "初始化会话名称", description = "前端在发起第一次对话后，需要主动调用这个接口，返回值为该会话的名称，用于前端指定动态刷新，而不是全面刷新")
    public Result<String> initializeConversationName(
            @PathVariable @Parameter(description = "会话ID", required = true) @NotNull Long conversationId,
            @RequestParam @Parameter(description = "用户输入", required = true) @NotNull String userInput,
            @RequestParam @Parameter(description = "模型输出", required = true) @NotNull String modelOutput
    ) {
        log.info("初始化会话名称:{}, {}, {}", conversationId, userInput, modelOutput);
        String result = conversationService.initializeConversationName(conversationId, userInput, modelOutput);
        return Result.success(result);
    }


}
