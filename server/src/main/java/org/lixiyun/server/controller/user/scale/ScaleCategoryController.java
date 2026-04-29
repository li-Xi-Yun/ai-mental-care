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
 * @author lixiyun
 * @since 2026-04-15 14:20
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/scale/category")
@Tag(name = "量表类别相关接口", description = "量表类别相关接口")
public class ScaleCategoryController {

    private final ScaleCategoryService scaleCategoryService;

    @GetMapping
    @Operation(summary = "展示所有量表类别", description = "展示所有量表类别信息")
    public Result<List<ScaleCategoryVO>> listCategories() {
        List<ScaleCategoryVO> result = scaleCategoryService.listCategories();
        return Result.success(result);
    }

}
