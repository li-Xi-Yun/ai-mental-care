package org.lixiyun.server.controller.user.scale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.vo.user.scale.ScaleCategoryVO;
import org.lixiyun.server.service.user.ScaleCategoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 量表类别前台接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/scale/category")
@Tag(name = "量表类别相关接口", description = "量表类别相关接口")
public class ScaleCategoryController {

    private final ScaleCategoryService scaleCategoryService;

    @GetMapping
    @Operation(summary = "量表类别列表", description = "返回启用中的量表类别列表，含各类别下可作答量表数量")
    public Result<List<ScaleCategoryVO>> listCategories() {
        log.info("查询量表类别列表");
        List<ScaleCategoryVO> result = scaleCategoryService.listCategories();
        return Result.success(result);
    }

}
