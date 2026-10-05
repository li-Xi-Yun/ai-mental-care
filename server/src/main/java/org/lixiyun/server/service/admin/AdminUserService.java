package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.user.AdminUserCreateDTO;
import org.lixiyun.pojo.dto.admin.user.AdminUserUpdateDTO;
import org.lixiyun.pojo.dto.admin.user.ResetPasswordDTO;
import org.lixiyun.pojo.dto.admin.user.UserQueryDTO;
import org.lixiyun.pojo.dto.admin.user.UserStatusDTO;
import org.lixiyun.pojo.vo.admin.user.UserVO;

/**
 * 管理员用户管理服务接口
 * <p>
 * 提供普通用户的分页查询、详情、新增、编辑、状态修改、密码重置、注销等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-04-20 14:39
 */
public interface AdminUserService {

    /**
     * 分页查询所有用户信息
     * <p>支持账号名、用户名、手机号模糊匹配和账号状态筛选</p>
     *
     * @param queryDTO 查询条件 {@link UserQueryDTO}
     * @return 分页结果 {@link PageResult}
     */
    PageResult<UserVO> listUsers(UserQueryDTO queryDTO);

    /**
     * 获取用户详情
     * <p>根据用户ID获取用户详细信息，用于编辑弹窗回显</p>
     *
     * @param id 用户ID
     * @return 用户信息 {@link UserVO}
     */
    UserVO getUserDetail(Long id);

    /**
     * 管理端新增普通用户
     * <p>创建普通用户并绑定普通用户角色</p>
     *
     * @param createDTO 新增用户信息 {@link AdminUserCreateDTO}
     */
    void createUser(AdminUserCreateDTO createDTO);

    /**
     * 管理端编辑普通用户
     * <p>更新用户基本信息，支持账号名变更（记录账号名修改时间）</p>
     *
     * @param id          用户ID
     * @param updateDTO   编辑用户信息 {@link AdminUserUpdateDTO}
     */
    void updateUser(Long id, AdminUserUpdateDTO updateDTO);

    /**
     * 修改用户账号状态
     * <p>支持封禁并填写封禁理由，恢复/异常/注销时清空封禁字段</p>
     *
     * @param statusDTO 状态修改DTO {@link UserStatusDTO}
     */
    void updateUserStatus(UserStatusDTO statusDTO);

    /**
     * 重置用户密码
     * <p>由管理员为用户设置新密码</p>
     *
     * @param id                用户ID
     * @param resetPasswordDTO  新密码 {@link ResetPasswordDTO}
     */
    void resetPassword(Long id, ResetPasswordDTO resetPasswordDTO);

    /**
     * 用户注销
     * <p>将用户账号状态置为注销(status=3)，并清空封禁字段</p>
     *
     * @param id 用户ID
     */
    void deleteUser(Long id);
}