package org.lixiyun.server.controller.admin.symtom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;
import org.lixiyun.pojo.dto.admin.symptom.AdminSymptomDictQueryDTO;
import org.lixiyun.pojo.dto.admin.symptom.SymptomDictDTO;
import org.lixiyun.pojo.vo.admin.symptom.SymptomDictOptionVO;
import org.lixiyun.pojo.vo.admin.symptom.SymptomDictVO;
import org.lixiyun.server.service.admin.AdminSymptomDictService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员症状词典相关接口
 *
 * @author lixiyun
 * @since 2026-08-15 15:13
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/symptom-dict")
@Tag(name = "管理员症状词典相关接口", description = "管理员症状词典相关接口")
public class AdminSymptomController {

    private final AdminSymptomDictService adminSymptomDictService;

    @PostMapping("/page")
//    @PreAuthorize("hasAuthority('symptom:dict:list')")
    @Operation(summary = "分页查询症状词典", description = "支持症状名称模糊匹配、分类筛选、状态筛选")
    public Result<PageResult<SymptomDictVO>> pageSymptomDict(@RequestBody @Validated AdminSymptomDictQueryDTO queryDTO) {
        log.info("分页查询症状词典：{}", queryDTO);
        PageResult<SymptomDictVO> result = adminSymptomDictService.pageSymptomDict(queryDTO);
        return Result.success(result);
    }

    @GetMapping("/{id}")
//    @PreAuthorize("hasAuthority('symptom:dict:list')")
    @Operation(summary = "获取症状词典详情", description = "根据ID获取症状词典详细信息")
    public Result<SymptomDictVO> getSymptomDictDetail(
            @PathVariable @NotNull @Parameter(description = "标准术语ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("获取症状词典详情，id：{}", id);
        SymptomDictVO vo = adminSymptomDictService.getSymptomDictDetail(id);
        return Result.success(vo);
    }

    @PostMapping
//    @PreAuthorize("hasAuthority('symptom:dict:create')")
    @Operation(summary = "新增症状词典", description = "新增一条症状标准术语记录")
    public Result<Void> createSymptomDict(@RequestBody @Validated(AddGroup.class) SymptomDictDTO dto) {
        log.info("新增症状词典：{}", dto);
        adminSymptomDictService.createSymptomDict(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
//    @PreAuthorize("hasAuthority('symptom:dict:update')")
    @Operation(summary = "修改症状词典", description = "修改症状标准术语记录")
    public Result<Void> updateSymptomDict(
            @PathVariable @NotNull @Parameter(description = "标准术语ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestBody @Validated(UpdateGroup.class) SymptomDictDTO dto) {
        log.info("修改症状词典，id：{}，dto：{}", id, dto);
        adminSymptomDictService.updateSymptomDict(id, dto);
        return Result.success();
    }

    @PutMapping("/{id}/status")
//    @PreAuthorize("hasAuthority('symptom:dict:update')")
    @Operation(summary = "启用/禁用症状词典", description = "只更新状态字段，0=禁用，1=启用")
    public Result<Void> updateSymptomDictStatus(
            @PathVariable @NotNull @Parameter(description = "标准术语ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestParam @Parameter(description = "状态：0=禁用，1=启用") @NotNull(message = "状态不能为空")
            @NumberOfRanges(min = 0, max = 1) Integer status) {
        log.info("启用/禁用症状词典，id：{}，status：{}", id, status);
        adminSymptomDictService.updateSymptomDictStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/batch")
//    @PreAuthorize("hasAuthority('symptom:dict:delete')")
    @Operation(summary = "批量删除症状词典", description = "根据ID数组批量删除症状标准术语记录")
    public Result<Void> batchDeleteSymptomDict(@RequestBody @NotEmpty List<Long> ids) {
        log.info("批量删除症状词典，ids：{}", ids);
        adminSymptomDictService.batchDeleteSymptomDict(ids);
        return Result.success();
    }

    @GetMapping("/options")
//    @PreAuthorize("hasAuthority('symptom:dict:list')")
    @Operation(summary = "获取症状词典下拉选项（分组）", description = "只返回启用的数据，按症状大类分组，用于前端选择框")
    public Result<List<SymptomDictOptionVO>> getSymptomDictOptions() {
        log.info("获取症状词典下拉选项");
        List<SymptomDictOptionVO> options = adminSymptomDictService.getSymptomDictOptions();
        return Result.success(options);
    }
}