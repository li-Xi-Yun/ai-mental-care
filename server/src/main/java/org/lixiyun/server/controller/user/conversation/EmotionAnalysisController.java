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
import org.lixiyun.pojo.vo.user.conversation.EmotionAnalysisDetailVO;
import org.lixiyun.pojo.vo.user.conversation.EmotionAnalysisVO;
import org.lixiyun.server.service.user.EmotionAnalysisService;
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
@RequestMapping("/user/conversation/emotion-analysis")
@Tag(name = "情绪分析相关接口", description = "情绪分析相关接口")
public class EmotionAnalysisController {

    private final EmotionAnalysisService emotionAnalysisService;

    @PostMapping("/list/{conversationId}")
    @Operation(summary = "情绪分析分页展示", description = "按创建时间逆序排列")
    public Result<PageResult<EmotionAnalysisVO>> listEmotionAnalysis(
            @PathVariable @Parameter(description = "会话ID") Long conversationId,
            @RequestBody @Validated PageBaseDTO pageBaseDTO
    ) {
        log.info("分页查询情绪分析: {}, {}, {}", conversationId, pageBaseDTO.getPageNum(), pageBaseDTO.getPageSize());
        PageResult<EmotionAnalysisVO> result = emotionAnalysisService.listEmotionAnalysis(conversationId, pageBaseDTO.getPageNum(), pageBaseDTO.getPageSize());
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