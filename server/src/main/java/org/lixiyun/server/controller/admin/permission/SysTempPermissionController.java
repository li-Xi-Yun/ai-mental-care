package org.lixiyun.server.controller.admin.permission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionGrantDTO;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionQueryDTO;
import org.lixiyun.pojo.vo.admin.permission.SysTempPermissionPageVO;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;
import org.lixiyun.server.service.admin.SysTempPermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统临时权限管理控制器
 * <p>
 * 提供临时权限的授予、作废、分页查询等功能，不修改用户角色，独立一套接口
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31 11:39
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/tempPerm")
@Tag(name = "系统临时权限管理接口", description = "系统临时权限管理接口")
public class SysTempPermissionController {

    private final SysTempPermissionService sysTempPermissionService;

    @PostMapping("/user/page")
    @PreAuthorize("hasAuthority('sys:tempPerm:page')")
    @Operation(summary = "分页查询前台用户临时权限人员", description = "分页查询前台用户临时权限人员列表")
    public Result<PageResult<SysTempPermissionPageVO>> pageUserTempPermissionPersons(
            @RequestBody @Validated SysTempPermissionQueryDTO queryDTO) {
        log.info("分页查询前台用户临时权限人员参数: {}", queryDTO);
        PageResult<SysTempPermissionPageVO> pageResult = sysTempPermissionService.pageUserTempPermissionPersons(queryDTO);
        return Result.success(pageResult);
    }

    @PostMapping("/admin/page")
    @PreAuthorize("hasAuthority('sys:tempPerm:page')")
    @Operation(summary = "分页查询后台管理员临时权限人员", description = "分页查询后台管理员临时权限人员列表")
    public Result<PageResult<SysTempPermissionPageVO>> pageAdminTempPermissionPersons(
            @RequestBody @Validated SysTempPermissionQueryDTO queryDTO) {
        log.info("分页查询后台管理员临时权限人员参数: {}", queryDTO);
        PageResult<SysTempPermissionPageVO> pageResult = sysTempPermissionService.pageAdminTempPermissionPersons(queryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/user/{personId}")
    @PreAuthorize("hasAuthority('sys:tempPerm:page')")
    @Operation(summary = "查询前台用户全部临时权限", description = "查询指定前台用户的全部临时权限")
    public Result<List<TempPermissionVO>> listUserTempPermissions(
            @PathVariable Long personId,
            @RequestParam(required = false) @Parameter(description = "临时权限状态 0有效 1手动作废 2已过期") @NumberOfRanges(min = 0, max = 2)  Integer status) {
        log.info("查询前台用户临时权限参数: personId={}, status={}", personId, status);
        List<TempPermissionVO> tempPermissionVOList = sysTempPermissionService.listUserTempPermissions(personId, status);
        return Result.success(tempPermissionVOList);
    }

    @GetMapping("/admin/{personId}")
    @PreAuthorize("hasAuthority('sys:tempPerm:page')")
    @Operation(summary = "查询后台管理员全部临时权限", description = "查询指定后台管理员的全部临时权限")
    public Result<List<TempPermissionVO>> listAdminTempPermissions(
            @PathVariable Long personId,
            @RequestParam(required = false) @Parameter(description = "临时权限状态 0有效 1手动作废 2已过期") @NumberOfRanges(min = 0, max = 2) Integer status) {
        log.info("查询后台管理员临时权限参数: personId={}, status={}", personId, status);
        List<TempPermissionVO> tempPermissionVOList = sysTempPermissionService.listAdminTempPermissions(personId, status);
        return Result.success(tempPermissionVOList);
    }

    @PostMapping("/grant")
    @PreAuthorize("hasAuthority('sys:tempPerm:grant')")
    @Operation(summary = "授予临时权限", description = "授予临时权限，批量插入person_temp_permission表，不修改用户角色")
    public Result<Void> grantTempPermission(
            @RequestBody @Validated SysTempPermissionGrantDTO grantDTO) {
        log.info("授予临时权限：{}", grantDTO);
        sysTempPermissionService.grantTempPermission(grantDTO);
        return Result.success();
    }

    @PutMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('sys:tempPerm:revoke')")
    @Operation(summary = "手动作废临时权限", description = "手动作废临时权限，status=1，不删除记录，审计保留")
    public Result<Void> revokeTempPermission(
            @PathVariable @Parameter(description = "临时权限记录ID") Long id) {
        log.info("手动作废临时权限，记录ID：{}", id);
        sysTempPermissionService.revokeTempPermission(id);
        return Result.success();
    }
}