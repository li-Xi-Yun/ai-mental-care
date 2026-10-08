package org.lixiyun.server.controller.tool;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.user.tool.ScaleConversationBindRecordDTO;
import org.lixiyun.pojo.dto.user.tool.ScaleConversationCompleteDTO;
import org.lixiyun.server.service.tool.ScaleConversationToolService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI对话量表工具前台接口
 * <p>
 * 承接对话量表卡片的人工交互回调：
 * <ul>
 *   <li>保存用户测评记录ID：用户进入答题页拿到 recordId 后回写，保证刷新后可恢复作答上下文</li>
 *   <li>用户作答完成：接收用户点击"是否答完题目"，触发分析并写入待对话注入结果</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/scale/tool")
@Tag(name = "AI对话量表工具相关接口", description = "AI对话量表工具相关接口")
public class ScaleConversationToolController {

    private final ScaleConversationToolService scaleConversationToolService;

    @PostMapping("/bind-record")
    @Operation(summary = "保存用户测评记录ID", description = "用户进入答题页拿到测评记录ID后回调保存到待处理记录，保证刷新后仍可恢复作答上下文")
    public Result<Void> bindRecord(@RequestBody @Validated ScaleConversationBindRecordDTO dto) {
        log.info("保存用户测评记录ID: toolId={}, conversationId={}, recordId={}",
                dto.getToolId(), dto.getConversationId(), dto.getRecordId());
        scaleConversationToolService.bindRecord(dto);
        return Result.success();
    }

    @PostMapping("/complete")
    @Operation(summary = "用户作答完成提交", description = "接收用户点击是否答完题目：已作答时读取库内答题明细生成分析并置为待对话注入，未作答时取消本次交互")
    public Result<Void> completeAnswer(@RequestBody @Validated ScaleConversationCompleteDTO dto) {
        log.info("用户作答完成提交: toolId={}, conversationId={}, recordId={}, answered={}",
                dto.getToolId(), dto.getConversationId(), dto.getRecordId(), dto.getAnswered());
        scaleConversationToolService.completeAnswer(dto);
        return Result.success();
    }

}
