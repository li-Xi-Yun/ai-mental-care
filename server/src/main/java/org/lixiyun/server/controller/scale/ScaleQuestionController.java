package org.lixiyun.server.controller.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.pojo.dto.scale.ScaleQuestionDTO;
import org.lixiyun.pojo.vo.scale.ScaleQuestionVO;
import org.lixiyun.server.service.ScaleQuestionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lixiyun
 * @since 2026-04-15 16:25
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/scale/questions")
@Tag(name = "量表题目相关接口", description = "量表题目相关接口")
public class ScaleQuestionController {

    private final ScaleQuestionService scaleQuestionService;

    @PostMapping
    @Operation(summary = "创建题目及选项", description = "级联创建题目和选项")
    public Result<?> createQuestionWithOptions(@RequestBody @Validated(AddGroup.class) ScaleQuestionDTO scaleQuestionDTO) {
        log.info("创建题目及选项，请求数据: {}", scaleQuestionDTO);
        scaleQuestionService.createWithOptions(scaleQuestionDTO);
        return Result.success();
    }

    @GetMapping("/{questionId}")
    @Operation(summary = "展示题目及选项", description = "同时展示题目与选项，量表需启用且未删除")
    public Result<?> getQuestionWithOptions(@PathVariable @Parameter(description = "题目ID") Long questionId) {
        log.info("获取题目及选项，题目ID: {}", questionId);
        ScaleQuestionVO result = scaleQuestionService.getWithOptions(questionId);
        return Result.success(result);
    }

    @DeleteMapping("/{questionId}")
    @Operation(summary = "删除题目及选项", description = "级联删除题目和选项")
    public Result<?> deleteQuestionWithOptions(
            @PathVariable @Parameter(description = "题目ID") Long questionId,
            @RequestParam @Parameter(description = "量表ID") Long scaleId) {
        log.info("删除题目及选项，题目ID: {}, 量表ID: {}", questionId, scaleId);
        scaleQuestionService.deleteWithOptions(questionId, scaleId);
        return Result.success();
    }

    @PutMapping("/{questionId}")
    @Operation(summary = "修改题目元数据", description = "仅修改题目元数据信息")
    public Result<?> updateQuestionMetadata(
            @PathVariable @Parameter(description = "题目ID") Long questionId,
            @Validated @RequestBody ScaleQuestionDTO scaleQuestionDTO) {
        log.info("更新题目元数据，题目ID: {}, 请求数据: {}", questionId, scaleQuestionDTO);
        scaleQuestionService.updateMetadata(questionId, scaleQuestionDTO);
        return Result.success();
    }
}
