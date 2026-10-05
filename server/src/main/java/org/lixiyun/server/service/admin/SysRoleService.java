package org.lixiyun.server.service.admin;

import jakarta.validation.constraints.NotEmpty;
import org.lixiyun.pojo.dto.admin.permission.SysRoleDTO;
import org.lixiyun.pojo.dto.admin.permission.SysRoleQueryDTO;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionTreeVO;
import org.lixiyun.pojo.vo.admin.permission.SysRoleDetailVO;
import org.lixiyun.pojo.vo.admin.permission.SysRolePageVO;
import org.lixiyun.pojo.vo.admin.permission.SysRoleSimpleVO;
import org.lixiyun.common.sql.core.result.PageResult;

import java.util.List;

/**
 * 系统角色服务接口
 * <p>
 * 提供角色的增删改查、状态管理、权限分配等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31
 */
public interface SysRoleService {

    /**
     * 分页查询角色列表
     * <p>
     * 支持角色名称模糊匹配和角色状态筛选
     * </p>
     *
     * @param queryDTO 查询条件 {@link SysRoleQueryDTO}
     * @return 分页结果 {@link PageResult}
     */
    PageResult<SysRolePageVO> pageRoleList(SysRoleQueryDTO queryDTO);

    /**
     * 查询单个角色详情
     * <p>
     * 根据角色ID查询角色详细信息
     * </p>
     *
     * @param id 角色ID
     * @return 角色详情信息 {@link SysRoleDetailVO}
     */
    SysRoleDetailVO getRoleDetail(Long id);

    /**
     * 新增角色
     * <p>
     * 新增角色信息，角色标识必须唯一
     * </p>
     *
     * @param roleDTO 角色信息 {@link SysRoleDTO}
     */
    void addRole(SysRoleDTO roleDTO);

    /**
     * 修改角色信息
     * <p>
     * 修改角色名称、备注等信息
     * </p>
     *
     * @param id      角色ID
     * @param roleDTO 角色信息 {@link SysRoleDTO}
     */
    void updateRole(Long id, SysRoleDTO roleDTO);

    /**
     * 修改角色状态
     * <p>
     * 启用或停用角色
     * </p>
     *
     * @param id     角色ID
     * @param status 角色状态：0=正常，1=停用
     */
    void updateRoleStatus(Long id, Integer status);

    /**
     * 删除角色
     * <p>
     * 逻辑删除角色，将status设置为2
     * </p>
     *
     * @param id 角色ID
     */
    void deleteRole(Long id);

    /**
     * 查询角色绑定的权限ID集合
     * <p>
     * 根据角色ID查询该角色绑定的权限ID集合
     * </p>
     *
     * @param id 角色ID
     * @return 权限集合
     */
    List<SysPermissionTreeVO> getRolePermissions(Long id);

    /**
     * 角色分配权限
     * <p>
     * 为角色分配权限，全量覆盖原有权限
     * </p>
     *
     * @param id                角色ID
     */
    void assignPermissions(Long id, @NotEmpty(message = "权限ID列表不能为空") List<Long> assignPermissionDTO);

    /**
     * 获取全部正常角色列表
     * <p>
     * 用于下拉选择框，只返回id和name
     * </p>
     *
     * @return 角色简单信息列表
     */
    List<SysRoleSimpleVO> getAllSimpleRoles();

    /**
     * 给人员分配角色
     * @param id 人员ID
     * @param roleIdList 角色ID列表
     * @return 是否分配成功
     */
    boolean assignRoles(Long id, List<Long> roleIdList);
}