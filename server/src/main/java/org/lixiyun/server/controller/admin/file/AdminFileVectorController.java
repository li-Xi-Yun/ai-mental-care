package org.lixiyun.server.controller.admin.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.vo.admin.file.FileVectorVO;
import org.lixiyun.server.service.admin.AdminFileVectorService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 文件向量管理相关接口
 *
 * @author lixiyun
 * @since 2026-05-02 19:20
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/file/vector")
@Tag(name = "文件向量相关接口", description = "文件向量相关接口")
public class AdminFileVectorController {

    private final AdminFileVectorService adminFileVectorService;

    @PostMapping("/{fileId}/load")
    @Operation(summary = "文件向量加载", description = "根据文件ID加载文件向量，校验文件状态后进行向量加载")
    public Result<Void> loadFileVector(
            @PathVariable @NotNull @Parameter(description = "文件ID", required = true, in = ParameterIn.PATH) Long fileId
    ) {
        log.info("文件向量加载请求接口，文件ID：{}", fileId);
        adminFileVectorService.loadFileVector(fileId);
        return Result.success();
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "文件向量删除", description = "根据文件ID删除文件向量数据")
    public Result<Void> deleteFileVector(
            @PathVariable @NotNull @Parameter(description = "文件ID", required = true, in = ParameterIn.PATH) Long fileId) {
        log.info("文件向量删除请求接口，文件ID：{}", fileId);
        adminFileVectorService.deleteFileVector(fileId);
        return Result.success();
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "文件向量分页查询", description = "根据文件ID分页查询文件向量数据，按二次分块索引排序")
    public Result<PageResult<FileVectorVO>> queryFileVector(
            @PathVariable @NotNull @Parameter(description = "文件ID", required = true, in = ParameterIn.PATH) Long fileId,
            @RequestParam(defaultValue = "1") @Parameter(description = "当前页码", in = ParameterIn.QUERY) Integer pageNum,
            @RequestParam(defaultValue = "10") @Parameter(description = "每页数量", in = ParameterIn.QUERY) Integer pageSize) {
        log.info("文件向量分页查询请求接口，文件ID：{}，页码：{}，每页数量：{}", fileId, pageNum, pageSize);
        PageResult<FileVectorVO> pageResult = adminFileVectorService.queryFileVector(fileId, pageNum, pageSize);
        return Result.success(pageResult);
    }

    @PostMapping("/{fileId}/interrupt")
    @Operation(summary = "文件向量解析中断", description = "中断指定文件的向量解析任务")
    public Result<Void> interruptFileVectorParsing(
            @PathVariable @NotNull @Parameter(description = "文件ID", required = true, in = ParameterIn.PATH) Long fileId) {
        log.info("文件向量解析中断请求接口，文件ID：{}", fileId);
        adminFileVectorService.interruptFileVectorParsing(fileId);
        return Result.success();
    }

}
