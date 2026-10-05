package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminAddDTO;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminQueryDTO;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminStatusUpdateDTO;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminUpdateDTO;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;
import org.lixiyun.pojo.vo.admin.sysadmin.AdminDetailVO;
import org.lixiyun.pojo.vo.admin.sysadmin.AdminPageVO;
import org.lixiyun.pojo.vo.admin.sysadmin.RoleVO;

import java.util.List;

/**
 * 管理员管理服务接口
 * <p>
 * 提供管理员的新增、分页查询、详情、编辑（含角色调整）、状态修改、注销等功能，
 * 该模块为超级管理员专属操作
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface SysAdminService {

    /**
     * 分页查询管理员列表
     * <p>
     * 支持管理员用户名模糊匹配和账号状态筛选
     * </p>
     *
     * @param queryDTO 查询条件 {@link AdminQueryDTO}
     * @return 分页结果 {@link PageResult}
     */
    PageResult<AdminPageVO> pageAdminList(AdminQueryDTO queryDTO);

    /**
     * 查询单个管理员详情
     * <p>
     * 根据管理员ID查询管理员详细信息
     * </p>
     *
     * @param id 管理员ID
     * @return 管理员详情信息 {@link AdminDetailVO}
     */
    AdminDetailVO getAdminDetail(Long id);

    /**
     * 新增管理员账号
     * <p>
     * 创建新的管理员账号，密码加密入库
     * </p>
     *
     * @param adminAddDTO 新增管理员信息 {@link AdminAddDTO}
     */
    void addAdmin(AdminAddDTO adminAddDTO);

    /**
     * 修改管理员基础信息
     * <p>
     * 修改管理员的用户名、邮箱、手机等基础信息
     * </p>
     *
     * @param adminUpdateDTO 修改管理员信息 {@link AdminUpdateDTO}
     */
    void updateAdmin(AdminUpdateDTO adminUpdateDTO);

    /**
     * 修改管理员账号状态
     * <p>
     * 启用或封禁管理员账号，封禁时需填写封禁时间和理由
     * </p>
     *
     * @param id        管理员ID
     * @param statusDTO 状态修改信息 {@link AdminStatusUpdateDTO}
     */
    void updateAdminStatus(Long id, AdminStatusUpdateDTO statusDTO);

    /**
     * 重置管理员密码
     * <p>
     * 重置指定管理员的密码为默认密码
     * </p>
     *
     * @param id 管理员ID
     */
    void resetAdminPassword(Long id);

    /**
     * 逻辑删除管理员
     * <p>
     * 将管理员标记为已删除状态（deleted=1）
     * </p>
     *
     * @param id 管理员ID
     */
    void deleteAdmin(Long id);

    /**
     * 查询管理员已分配的角色列表
     * <p>
     * 根据管理员ID查询其已分配的角色信息
     * </p>
     *
     * @param id 管理员ID
     * @return 角色列表 {@link List}
     */
    List<RoleVO> getAdminRoles(Long id);

    /**
     * 给管理员分配角色
     * <p>
     * 为管理员分配角色，全量覆盖（先删后插）
     * </p>
     *
     * @param id         管理员ID
     * @param roleIdList 角色ID列表
     */
    void assignRolesToAdmin(Long id, List<Long> roleIdList);

    /**
     * 查询管理员临时权限记录
     * <p>
     * 根据管理员ID查询其临时权限记录
     * </p>
     *
     * @param id     管理员ID
     * @param status
     * @return 临时权限记录 {@link List}
     */
    List<TempPermissionVO> getAdminTempPermissions(Long id, Integer status);
}