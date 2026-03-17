package org.lixiyun.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.constant.LoginConstant;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.constant.RoleConstant;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.verification.code.utils.VerificationCodeUtil;
import org.lixiyun.pojo.dto.user.LoginUserDTO;
import org.lixiyun.pojo.dto.user.PasswordDTO;
import org.lixiyun.pojo.dto.user.RegisterUserDTO;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.pojo.tool.LoginUser;
import org.lixiyun.pojo.vo.user.LoginResultVO;
import org.lixiyun.server.mapper.PermissionMapper;
import org.lixiyun.server.mapper.PersonRoleMapper;
import org.lixiyun.server.mapper.RoleMapper;
import org.lixiyun.server.mapper.UserMapper;
import org.lixiyun.server.service.AccountService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * @author lixiyun
 * @since 2025-12-21 15:14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final PasswordEncoder passwordEncoder;

    private final UserMapper userMapper;

    private final RoleMapper roleMapper;

    private final PermissionMapper permissionMapper;

    private final PersonRoleMapper personRoleMapper;

    @Override
    @Transactional
    public void register(RegisterUserDTO registerUserDTO) {
        // 校验验证码是否正确
        VerificationCodeUtil.judgmentCode(registerUserDTO.getCode(), registerUserDTO.getKey());

        User user = BeanUtil.copyProperties(registerUserDTO, User.class);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);

        // 新增该用户普通用户权限
        personRoleMapper.saveUserRole(user.getId(), RoleConstant.USER_ROLE);
    }

    @Override
    public LoginResultVO login(LoginUserDTO user) {
        User userProfile;

        // 验证验证码是否正确
        VerificationCodeUtil.judgmentCode(user.getCode(), user.getKey());

        // 查询数据库中是否存在该账号
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(User::getUsername, user.getUsername());
        userProfile = userMapper.selectOne(queryWrapper);
        if (userProfile == null) {
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_AND_ACCOUNT_ERROR);
        }

        // 判断该账号状态是否处于正常状态
        if(!Objects.equals(userProfile.getStatus(), LoginConstant.ACCOUNT_NORMAL)){
            throw new BusinessException(AuthenticationExceptionEnum.USER_BANNED);
        }

        // 密码校验
        boolean matches = passwordEncoder.matches(user.getPassword(), userProfile.getPassword());
        if (!matches) {
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_AND_ACCOUNT_ERROR);
        }

        // 查询用户权限
        List<String> permissions = permissionMapper.queryPermsByPersonId(userProfile.getId());

        // 查询用户角色
        List<String> roles = roleMapper.queryRoleByPersonId(userProfile.getId());

        // 认证成功生成token，根据userId生成token
        LoginUser loginUser = new LoginUser(userProfile, permissions, roles);
        String token = JwtUtil.createJwtWithRedis(loginUser, true);

        return LoginResultVO.builder()
                .token(token)
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    @Override
    public void logout() {
        LoginUser currentLoginUser = UserInfoThreadLocalUtil.getCurrentLoginUserThrow();
        Long userId = currentLoginUser.getBasicsUser().getId();
        JwtUtil.deleteJwtWithRedis(userId);
    }

    @Override
    public void pwdUpdate(PasswordDTO passwordDTO) {
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


}
