package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.admin.LoginAdminDTO;
import org.lixiyun.pojo.dto.admin.admin.RegisterAdminDTO;
import org.lixiyun.pojo.dto.user.user.PasswordDTO;
import org.lixiyun.pojo.vo.user.user.LoginResultVO;

/**
 * 管理员账号服务接口
 * <p>
 * 提供管理员注册、登录、登出、密码修改等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-04-18 20:14
 */
public interface AdminAccountService {

    /**
     * 管理员注册功能
     * <p>
     * 接收管理员注册信息，完成管理员账户的创建
     * </p>
     *
     * @param registerAdminDTO 管理员注册信息数据传输对象 {@link RegisterAdminDTO}
     */
    void register(RegisterAdminDTO registerAdminDTO);

    /**
     * 登录功能
     * <p>
     * 管理员登录功能，返回token
     * </p>
     *
     * @param loginAdminDTO 登录信息数据传输对象(包含用户名、密码、验证码等) {@link LoginAdminDTO}
     * @return 管理员权限、角色、token信息 {@link LoginResultVO}
     */
    LoginResultVO login(LoginAdminDTO loginAdminDTO);

    /**
     * 管理员登出功能
     * <p>
     * 清除管理员的当前会话，使其退出登录状态
     * </p>
     */
    void logout();

    /**
     * 密码修改功能
     * <p>
     * 允许已登录管理员修改自己的账户密码
     * </p>
     *
     * @param passwordDTO 密码更新信息数据传输对象(包含旧密码和新密码) {@link PasswordDTO}
     */
    void pwdUpdate(PasswordDTO passwordDTO);
}
