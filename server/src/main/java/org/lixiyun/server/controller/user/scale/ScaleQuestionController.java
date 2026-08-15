  package org.lixiyun.server.controller.user.scale;

  import io.swagger.v3.oas.annotations.Operation;
  import io.swagger.v3.oas.annotations.Parameter;
  import io.swagger.v3.oas.annotations.tags.Tag;
  import jakarta.validation.constraints.NotNull;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.lixiyun.common.core.result.Result;
  import org.lixiyun.common.sql.core.result.PageResult;
  import org.lixiyun.common.validation.annotation.NumberOfRanges;
  import org.lixiyun.pojo.vo.user.scale.ScaleQuestionVO;
  import org.lixiyun.server.service.user.ScaleQuestionService;
  import org.springframework.validation.annotation.Validated;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RequestParam;
  import org.springframework.web.bind.annotation.RestController;

/**
 * @author lixiyun
 * @since 2026-04-15 16:25
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/scale/questions")
@Tag(name = "量表题目相关接口", description = "量表题目相关接口")
public class ScaleQuestionController {

    private final ScaleQuestionService scaleQuestionService;

    @GetMapping("/list")
    @Operation(summary = "分页查询量表题目列表", description = "分页展示指定量表下的所有题目及选项，量表需启用且未删除")
    public Result<PageResult<ScaleQuestionVO>> listQuestionsWithOptions(
            @RequestParam @Parameter(description = "量表ID", required = true) @NotNull Long scaleId,
            @RequestParam @Parameter(description = "当前页码", required = true) @NotNull @NumberOfRanges Integer pageNum,
            @RequestParam @Parameter(description = "每页数量", required = true) @NotNull @NumberOfRanges Integer pageSize
    ) {
        log.info("分页查询量表题目列表，量表ID: {}, 页码: {}, 每页数量: {}", scaleId, pageNum, pageSize);
        PageResult<ScaleQuestionVO> result = scaleQuestionService.listQuestionsWithOptions(scaleId, pageNum, pageSize);
        return Result.success(result);
    }

}
