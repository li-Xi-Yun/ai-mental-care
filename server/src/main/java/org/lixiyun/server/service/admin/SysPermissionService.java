package org.lixiyun.server.service.admin;


import org.lixiyun.pojo.dto.admin.permission.SysPermissionDTO;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionDetailVO;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionTreeVO;

import java.util.List;

/**
 * 系统权限服务接口
 * <p>
 * 提供权限的增删改查、权限树查询等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31
 */
public interface SysPermissionService {

    /**
     * 查询权限树形列表
     * <p>
     * 按权限分组组织权限列表，用于角色授权弹窗、临时权限授予弹窗勾选树
     * </p>
     *
     * @return 权限树形列表 {@link SysPermissionTreeVO}
     */
    List<SysPermissionTreeVO> getPermissionTree();

    /**
     * 查询单个权限详情
     * <p>
     * 根据权限ID查询权限详细信息
     * </p>
     *
     * @param id 权限ID
     * @return 权限详情信息 {@link SysPermissionDetailVO}
     */
    SysPermissionDetailVO getPermissionDetail(Long id);

    /**
     * 新增权限
     * <p>
     * 新增权限点，权限标识符必须唯一
     * </p>
     *
     * @param permissionDTO 权限信息 {@link SysPermissionDTO}
     */
    void addPermission(SysPermissionDTO permissionDTO);

    /**
     * 修改权限信息
     * <p>
     * 修改权限标识符、名称、分组等信息
     * </p>
     *
     * @param id             权限ID
     * @param permissionDTO 权限信息 {@link SysPermissionDTO}
     */
    void updatePermission(Long id, SysPermissionDTO permissionDTO);

    /**
     * 删除权限
     * <p>
     * 逻辑删除权限
     * </p>
     *
     * @param id 权限ID
     */
    void deletePermission(Long id);
}