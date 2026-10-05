package org.lixiyun.server.controller.admin.permission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionGrantDTO;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionQueryDTO;
import org.lixiyun.pojo.vo.admin.permission.SysTempPermissionPageVO;
import org.lixiyun.server.service.admin.SysTempPermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('sys:tempPerm:page')")
    @Operation(summary = "分页查询临时权限记录", description = """
            全部临时权限记录分页（审计页面，管理员查看所有用户的临时授权记录）
            这里前端列表展示用户列表，点击列表中的用户时，展示该用户的临时授权记录
            """)
    public Result<PageResult<SysTempPermissionPageVO>> pageTempPermissionList(
            @RequestBody @Validated SysTempPermissionQueryDTO queryDTO) {
        log.info("分页查询临时权限记录：{}", queryDTO);
        PageResult<SysTempPermissionPageVO> pageResult = sysTempPermissionService.pageTempPermissionList(queryDTO);
        return Result.success(pageResult);
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