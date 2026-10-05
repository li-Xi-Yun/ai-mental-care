package org.lixiyun.server.controller.admin.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.admin.RegisterAdminDTO;
import org.lixiyun.pojo.dto.admin.user.AdminQueryDTO;
import org.lixiyun.pojo.dto.admin.user.AdminStatusDTO;
import org.lixiyun.pojo.dto.admin.user.AdminUpdateDTO;
import org.lixiyun.pojo.vo.admin.user.AdminVO;
import org.lixiyun.server.service.admin.AdminManageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员管理相关接口
 * <p>管理员新增、列表、详情、编辑（含角色调整）、状态修改、注销，全部为超级管理员专属操作</p>
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/admin")
@Tag(name = "管理员管理相关接口", description = "管理员管理相关接口（超级管理员专属）")
public class AdminManageController {

    private final AdminManageService adminManageService;

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('admin:admin:create')")
    @Operation(summary = "新增管理员", description = "新增管理员，账号名由系统自动生成，绑定普通管理员角色")
    public Result<Void> registerAdmin(@RequestBody @Validated RegisterAdminDTO registerAdminDTO) {
        log.info("新增管理员：{}", registerAdminDTO);
        adminManageService.registerAdmin(registerAdminDTO);
        return Result.success();
    }

    @PostMapping("/list")
    @PreAuthorize("hasAuthority('admin:admin:list')")
    @Operation(summary = "查询所有管理员", description = "分页查询所有管理员信息，支持账号名/用户名/手机号模糊匹配和账号状态筛选")
    public Result<PageResult<AdminVO>> listAdmins(@RequestBody @Validated AdminQueryDTO queryDTO) {
        log.info("查询所有管理员：{}", queryDTO);
        PageResult<AdminVO> result = adminManageService.listAdmins(queryDTO);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:admin:list')")
    @Operation(summary = "获取管理员详情", description = "根据管理员ID获取管理员详细信息，用于编辑弹窗回显")
    public Result<AdminVO> getAdminDetail(
            @PathVariable @NotNull @Parameter(description = "管理员ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("获取管理员详情，管理员id：{}", id);
        AdminVO vo = adminManageService.getAdminDetail(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:admin:update')")
    @Operation(summary = "编辑管理员 / 调整角色", description = "编辑管理员基本信息，支持账号名变更与角色调整")
    public Result<Void> updateAdmin(
            @PathVariable @NotNull @Parameter(description = "管理员ID", required = true, in = ParameterIn.PATH) Long id,
            @RequestBody @Validated AdminUpdateDTO updateDTO) {
        log.info("编辑管理员，管理员id：{}，dto：{}", id, updateDTO);
        adminManageService.updateAdmin(id, updateDTO);
        return Result.success();
    }

    @PatchMapping("/status")
    @PreAuthorize("hasAuthority('admin:admin:update')")
    @Operation(summary = "管理员状态修改", description = "修改管理员账号状态，支持封禁并填写封禁理由")
    public Result<Void> updateAdminStatus(@RequestBody @Validated AdminStatusDTO statusDTO) {
        log.info("管理员状态修改：{}", statusDTO);
        adminManageService.updateAdminStatus(statusDTO);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:admin:delete')")
    @Operation(summary = "管理员注销", description = "将管理员账号状态置为注销(status=3)，不能注销自己")
    public Result<Void> deleteAdmin(
            @PathVariable @NotNull @Parameter(description = "管理员ID", required = true, in = ParameterIn.PATH) Long id) {
        log.info("管理员注销，管理员id：{}", id);
        adminManageService.deleteAdmin(id);
        return Result.success();
    }

}