package org.lixiyun.server.controller.user.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.base.PageBaseDTO;
import org.lixiyun.pojo.dto.user.conversation.EmotionDiagnosisFeedbackDTO;
import org.lixiyun.pojo.dto.user.conversation.EmotionDiagnosisQueryDTO;
import org.lixiyun.pojo.vo.user.conversation.DiagnosisConversationVO;
import org.lixiyun.pojo.vo.user.conversation.EmotionDiagnosisListVO;
import org.lixiyun.pojo.vo.user.conversation.EmotionDiagnosisVO;
import org.lixiyun.server.service.user.EmotionDiagnosisService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lixiyun
 * @since 2026-08-15 14:15
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/conversation/emotion-diagnosis")
@Tag(name = "情感诊断书相关接口", description = "情感诊断书相关接口")
public class EmotionDiagnosisController {

    private final EmotionDiagnosisService emotionDiagnosisService;

    @PostMapping("/list")
    @Operation(summary = "用户个人中心分页查询拥有诊断的会话列表", description = "按会话名称模糊匹配，按诊断更新时间逆序排列")
    public Result<PageResult<DiagnosisConversationVO>> listByUserCenter(
            @RequestBody @Validated EmotionDiagnosisQueryDTO queryDTO
    ) {
        log.info("用户个人中心分页查询拥有诊断的会话列表: pageNum={}, pageSize={}, conversationName={}",
                queryDTO.getPageNum(), queryDTO.getPageSize(), queryDTO.getConversationName());
        PageResult<DiagnosisConversationVO> result = emotionDiagnosisService.listByUserCenter(queryDTO);
        return Result.success(result);
    }

    @PostMapping("/list/{conversationId}")
    @Operation(summary = "按会话ID分页查询诊断书", description = "返回指定会话下的所有诊断书，按更新时间逆序排列")
    public Result<PageResult<EmotionDiagnosisListVO>> listByConversationId(
            @PathVariable @Parameter(description = "会话ID", required = true) @NotNull Long conversationId,
            @RequestBody @Validated PageBaseDTO pageBaseDTO
    ) {
        log.info("按会话ID分页查询诊断书: conversationId={}, pageNum={}, pageSize={}",
                conversationId, pageBaseDTO.getPageNum(), pageBaseDTO.getPageSize());
        PageResult<EmotionDiagnosisListVO> result = emotionDiagnosisService.listByConversationId(
                conversationId, pageBaseDTO.getPageNum(), pageBaseDTO.getPageSize());
        return Result.success(result);
    }

    @GetMapping("/detail/{diagnosisId}")
    @Operation(summary = "诊断书详情", description = "获取指定诊断书的完整详情数据")
    public Result<EmotionDiagnosisVO> getDiagnosisDetail(
            @PathVariable @Parameter(description = "诊断书ID", required = true) @NotNull Long diagnosisId
    ) {
        log.info("获取诊断书详情: {}", diagnosisId);
        EmotionDiagnosisVO result = emotionDiagnosisService.getDiagnosisDetail(diagnosisId);
        return Result.success(result);
    }

    @PutMapping("/{diagnosisId}/feedback")
    @Operation(summary = "用户反馈诊断书", description = "用户对指定诊断书提交反馈信息，包括打分、认同度、采纳情况等")
    public Result<Void> updateFeedback(
            @PathVariable @Parameter(description = "诊断书ID", required = true) @NotNull Long diagnosisId,
            @RequestBody @Validated EmotionDiagnosisFeedbackDTO feedbackDTO
    ) {
        log.info("用户反馈诊断书: diagnosisId={}, feedback={}", diagnosisId, feedbackDTO);
        emotionDiagnosisService.updateFeedback(diagnosisId, feedbackDTO);
        return Result.success();
    }

}