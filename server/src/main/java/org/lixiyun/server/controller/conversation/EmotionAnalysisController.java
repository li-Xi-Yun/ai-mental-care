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
import org.lixiyun.pojo.vo.conversation.EmotionAnalysisDetailVO;
import org.lixiyun.pojo.vo.conversation.EmotionAnalysisVO;
import org.lixiyun.pojo.vo.conversation.EmotionDiagnosisVO;
import org.lixiyun.server.service.EmotionAnalysisService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lixiyun
 * @since 2026-03-19 20:25
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/conversation/emotion-analysis")
@Tag(name = "情绪分析相关接口", description = "情绪分析相关接口")
public class EmotionAnalysisController {

    private final EmotionAnalysisService emotionAnalysisService;

    @GetMapping("/diagnosis/{conversationId}")
    @Operation(summary = "获取诊断书", description = "获取指定会话的情绪诊断书")
    public Result<EmotionDiagnosisVO> getDiagnosis(@PathVariable @Parameter(description = "会话ID", required = true) @NotNull Long conversationId) {
        log.info("获取诊断书: {}", conversationId);
        EmotionDiagnosisVO result = emotionAnalysisService.getDiagnosis(conversationId);
        return Result.success(result);
    }

    @GetMapping("/list/{conversationId}")
    @Operation(summary = "情绪分析分页展示", description = "按创建时间逆序排列")
    public Result<PageResult<EmotionAnalysisVO>> listEmotionAnalysis(
            @PathVariable @Parameter(description = "会话ID") Long conversationId,
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize
    ) {
        log.info("分页查询情绪分析: {}, {}, {}", conversationId, pageNum, pageSize);
        PageResult<EmotionAnalysisVO> result = emotionAnalysisService.listEmotionAnalysis(conversationId, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/detail/{analysisId}")
    @Operation(summary = "情绪分析详情", description = "获取情绪分析详情及对应轮次的对话")
    public Result<EmotionAnalysisDetailVO> getEmotionAnalysisDetail(
            @PathVariable @Parameter(description = "情绪分析ID", required = true) @NotNull Long analysisId) {
        log.info("获取情绪分析详情: {}", analysisId);
        EmotionAnalysisDetailVO result = emotionAnalysisService.getEmotionAnalysisDetail(analysisId);
        return Result.success(result);
    }

}
