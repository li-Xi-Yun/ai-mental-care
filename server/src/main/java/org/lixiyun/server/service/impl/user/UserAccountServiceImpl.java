package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.constant.RoleConstant;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.verification.code.utils.VerificationCodeUtil;
import org.lixiyun.pojo.dto.user.user.LoginUserDTO;
import org.lixiyun.pojo.dto.user.user.PasswordDTO;
import org.lixiyun.pojo.dto.user.user.RegisterUserDTO;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.pojo.tool.LoginUser;
import org.lixiyun.pojo.vo.user.user.LoginResultVO;
import org.lixiyun.pojo.vo.user.user.LoginUserInfoVO;
import org.lixiyun.server.mapper.PermissionMapper;
import org.lixiyun.server.mapper.PersonRoleMapper;
import org.lixiyun.server.mapper.RoleMapper;
import org.lixiyun.server.mapper.UserMapper;
import org.lixiyun.server.service.user.UserAccountService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author lixiyun
 * @since 2025-12-21 15:14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final PasswordEncoder passwordEncoder;

    private final UserMapper userMapper;

    private final RoleMapper roleMapper;

    private final PermissionMapper permissionMapper;

    private final PersonRoleMapper personRoleMapper;

    @Override
    @Transactional
    public void register(RegisterUserDTO registerUserDTO) {
        log.info("用户注册，账号名：{}，用户名：{}", registerUserDTO.getLoginAccount(), registerUserDTO.getUsername());

        // 校验验证码是否正确
        VerificationCodeUtil.judgmentCode(registerUserDTO.getCode(), registerUserDTO.getKey());

        // 校验账号名唯一性
        checkLoginAccountUniqueness(registerUserDTO.getLoginAccount());

        User user = BeanUtil.copyProperties(registerUserDTO, User.class);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        log.info("用户注册成功，用户id：{}", user.getId());

        // 新增该用户普通用户权限
        personRoleMapper.saveUserRole(user.getId(), RoleConstant.USER_ROLE);
    }

    @Override
    public LoginResultVO login(LoginUserDTO loginUserDTO) {
        log.info("用户登录，账号名：{}", loginUserDTO.getLoginAccount());

        // 验证验证码是否正确
        VerificationCodeUtil.judgmentCode(loginUserDTO.getCode(), loginUserDTO.getKey());

        // 查询数据库中是否存在该账号
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(User::getLoginAccount, loginUserDTO.getLoginAccount());
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            log.warn("用户登录失败：账号不存在，账号名：{}", loginUserDTO.getLoginAccount());
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_AND_ACCOUNT_ERROR);
        }

        // 判断该账号状态是否处于正常状态
        if (user.isAbnormal()) {
            log.warn("用户登录失败：账号异常，账号名：{}", loginUserDTO.getLoginAccount());
            throw new BusinessException(AuthenticationExceptionEnum.USER_STATUS_ABNORMAL);
        }

        if (user.isBanned()) {
            log.warn("用户登录失败：账号被封禁，账号名：{}", loginUserDTO.getLoginAccount());
            throw new BusinessException(AuthenticationExceptionEnum.USER_BANNED);
        }

        if (user.isLogout()) {
            log.warn("用户登录失败：账号已注销，账号名：{}", loginUserDTO.getLoginAccount());
            throw new BusinessException(AuthenticationExceptionEnum.USER_BANNED);
        }

        // 密码校验
        boolean matches = passwordEncoder.matches(loginUserDTO.getPassword(), user.getPassword());
        if (!matches) {
            log.warn("用户登录失败：密码错误，账号名：{}", loginUserDTO.getLoginAccount());
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_AND_ACCOUNT_ERROR);
        }

        // 查询管理员权限
        List<String> permissions = permissionMapper.queryPermsByPersonId(user.getId());

        // 查询管理员角色
        List<String> roles = roleMapper.queryRoleKeysByPersonId(user.getId());

        // 认证成功生成token，根据userId生成token
        LoginUser loginUser = new LoginUser(user, permissions, roles);
        String token = JwtUtil.createJwtWithRedis(loginUser, true, JwtType.USER);

        // 组装登录返回结果：token + 角色 + 用户简要信息
        LoginUserInfoVO userInfo = LoginUserInfoVO.builder()
                .id(user.getId())
                .loginAccount(user.getLoginAccount())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .build();

        return LoginResultVO.builder()
                .token(token)
                .headerName(JwtUtil.getTokenName(JwtType.USER))
                .roles(roles)
                .userInfo(userInfo)
                .build();
    }

    @Override
    public void logout() {
        LoginUser currentLoginUser = UserInfoThreadLocalUtil.getCurrentLoginUserThrow();
        Long userId = currentLoginUser.getBasicsUser().getId();
        JwtUtil.deleteJwtWithRedis(userId, JwtType.USER);
    }

    @Override
    public void pwdUpdate(PasswordDTO passwordDTO) {
        log.info("用户密码修改");

        // 获取当前用户id
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        // 校验用户原密码是否正确
        User user = userMapper.selectById(currentId);
        if (!passwordEncoder.matches(passwordDTO.getOriginalPassword(), user.getPassword())) {
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_ERROR);
        }

        // 新密码加密
        String encryptedNewPassword = passwordEncoder.encode(passwordDTO.getPassword());

        // 更新用户密码
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().eq(User::getId, currentId).set(User::getPassword, encryptedNewPassword);
        userMapper.update(updateWrapper);
    }

    /**
     * 校验账号名是否唯一
     *
     * @param loginAccount 账号名
     */
    private void checkLoginAccountUniqueness(String loginAccount) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(User::getLoginAccount, loginAccount);
        if (userMapper.selectCount(queryWrapper) > 0) {
            log.warn("账号名已存在：{}", loginAccount);
            throw new BusinessException(AuthenticationExceptionEnum.LOGIN_ACCOUNT_EXIST);
        }
    }

}