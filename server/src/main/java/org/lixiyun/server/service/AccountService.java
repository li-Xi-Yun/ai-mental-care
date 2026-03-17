package org.lixiyun.server.service;

import org.lixiyun.pojo.dto.user.LoginUserDTO;
import org.lixiyun.pojo.dto.user.PasswordDTO;
import org.lixiyun.pojo.dto.user.RegisterUserDTO;
import org.lixiyun.pojo.vo.user.LoginResultVO;

/**
 * 账户服务接口
 * <p>
 * 提供用户账户相关的核心业务功能，包括用户注册、用户名登录、邮箱登录、
 * 登出、密码修改和忘记密码等操作
 * </p>
 */
public interface AccountService {

    /**
     * 用户注册功能
     * <p>
     * 接收用户注册信息，完成用户账户的创建
     * </p>
     *
     * @param registerUserDTO 用户注册信息数据传输对象
     */
    void register(RegisterUserDTO registerUserDTO);

    /**
     * 登录功能
     * <p>
     * 用户登录功能，返回token
     * </p>
     *
     * @param user 登录信息数据传输对象(包含用户名、密码、验证码等) {@link LoginUserDTO}
     * @return 用户权限、角色、token信息 {@link LoginResultVO}
     */
    LoginResultVO login(LoginUserDTO user);

    /**
     * 用户登出功能
     * <p>
     * 清除用户的当前会话，使其退出登录状态
     * </p>
     */
    void logout();

    /**
     * 密码修改功能
     * <p>
     * 允许已登录用户修改自己的账户密码
     * </p>
     *
     * @param pwdUpdateDTO 密码更新信息数据传输对象(包含旧密码和新密码)
     */
    void pwdUpdate(PasswordDTO passwordDTO);

}