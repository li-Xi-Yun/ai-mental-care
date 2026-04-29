package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.user.AdminQueryDTO;
import org.lixiyun.pojo.dto.admin.user.AdminStatusDTO;
import org.lixiyun.pojo.dto.admin.user.UserQueryDTO;
import org.lixiyun.pojo.dto.admin.user.UserStatusDTO;
import org.lixiyun.pojo.vo.admin.user.AdminVO;
import org.lixiyun.pojo.vo.admin.user.UserVO;

/**
 * 管理员用户管理服务接口
 * <p>
 * 提供用户和管理员的查询、状态修改等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-04-20 14:39
 */
public interface AdminUserService {

    /**
     * 分页查询所有用户信息
     * <p>支持用户名模糊匹配和账号状态筛选</p>
     *
     * @param queryDTO 查询条件 {@link UserQueryDTO}
     * @return 分页结果 {@link PageResult}
     */
    PageResult<UserVO> listUsers(UserQueryDTO queryDTO);

    /**
     * 分页查询所有普通管理员信息
     * <p>支持管理员名模糊匹配和账号状态筛选</p>
     *
     * @param queryDTO 查询条件 {@link AdminQueryDTO}
     * @return 分页结果 {@link PageResult}
     */
    PageResult<AdminVO> listAdmins(AdminQueryDTO queryDTO);

    /**
     * 修改用户账号状态
     * <p>支持封禁并填写封禁理由</p>
     *
     * @param statusDTO 状态修改DTO {@link UserStatusDTO}
     */
    void updateUserStatus(UserStatusDTO statusDTO);

    /**
     * 修改管理员账号状态
     * <p>支持封禁并填写封禁理由和封禁时间</p>
     *
     * @param statusDTO 状态修改DTO {@link AdminStatusDTO}
     */
    void updateAdminStatus(AdminStatusDTO statusDTO);
}
