package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.sysuser.SysUserQueryDTO;
import org.lixiyun.pojo.dto.admin.sysuser.SysUserStatusUpdateDTO;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;
import org.lixiyun.pojo.vo.admin.sysadmin.RoleVO;
import org.lixiyun.pojo.vo.admin.sysuser.SysUserDetailVO;
import org.lixiyun.pojo.vo.admin.sysuser.SysUserPageVO;

import java.util.List;

/**
 * 管理员用户管理服务接口
 * <p>
 * 提供普通用户的分页查询、详情、新增、编辑、状态修改、密码重置、注销等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-04-20 14:39
 */
public interface SysUserService {

    /**
     * 分页查询用户列表
     * <p>
     * 支持用户名模糊匹配和账号状态筛选
     * </p>
     *
     * @param queryDTO 查询条件 {@link SysUserQueryDTO}
     * @return 分页结果 {@link PageResult}
     */
    PageResult<SysUserPageVO> pageUserList(SysUserQueryDTO queryDTO);

    /**
     * 查询单个用户详情
     * <p>
     * 根据用户ID查询用户详细信息
     * </p>
     *
     * @param id 用户ID
     * @return 用户详情信息 {@link SysUserDetailVO}
     */
    SysUserDetailVO getUserDetail(Long id);

    /**
     * 修改用户账号状态
     * <p>
     * 启用或封禁用户账号，封禁时需填写封禁时间和理由
     * </p>
     *
     * @param id        用户ID
     * @param statusDTO 状态修改信息 {@link SysUserStatusUpdateDTO}
     */
    void updateUserStatus(Long id, SysUserStatusUpdateDTO statusDTO);

    /**
     * 查询用户已分配的角色列表
     * <p>
     * 根据用户ID查询其已分配的角色信息
     * </p>
     *
     * @param id 用户ID
     * @return 角色列表 {@link List}
     */
    List<RoleVO> getUserRoles(Long id);

    /**
     * 给用户分配角色
     * <p>
     * 为用户分配角色，全量覆盖（先删后插）
     * </p>
     *
     * @param id           用户ID
     * @param roleIdList 角色ID列表 {@link List}
     */
    void assignRolesToUser(Long id, List<Long> roleIdList);

    /**
     * 查询用户临时权限记录
     * <p>
     * 查询该用户全部临时权限记录，包括临时权限、到期时间、授予原因
     * </p>
     *
     * @param id     用户ID
     * @param status
     * @return 临时权限记录列表 {@link List}
     */
    List<TempPermissionVO> getUserTempPermissions(Long id, Integer status);
}