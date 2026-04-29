package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.constant.LoginConstant;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.constant.RoleConstant;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.verification.code.utils.VerificationCodeUtil;
import org.lixiyun.pojo.dto.admin.admin.LoginAdminDTO;
import org.lixiyun.pojo.dto.admin.admin.RegisterAdminDTO;
import org.lixiyun.pojo.dto.user.user.PasswordDTO;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.tool.LoginUser;
import org.lixiyun.pojo.vo.user.user.LoginResultVO;
import org.lixiyun.server.mapper.AdminMapper;
import org.lixiyun.server.mapper.PermissionMapper;
import org.lixiyun.server.mapper.PersonRoleMapper;
import org.lixiyun.server.mapper.RoleMapper;
import org.lixiyun.server.service.admin.AdminAccountService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 管理员账号服务实现类
 * <p>
 * 提供管理员注册、登录、登出、密码修改等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-04-18 20:15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {

    private final PasswordEncoder passwordEncoder;

    private final AdminMapper adminMapper;

    private final RoleMapper roleMapper;

    private final PermissionMapper permissionMapper;

    private final PersonRoleMapper personRoleMapper;

    @Override
    @Transactional
    public void register(RegisterAdminDTO registerAdminDTO) {
        log.debug("开始管理员注册流程，用户名：{}", registerAdminDTO.getUsername());

        // 校验验证码是否正确
        VerificationCodeUtil.judgmentCode(registerAdminDTO.getCode(), registerAdminDTO.getKey());

        // 复制属性并加密密码
        Admin admin = BeanUtil.copyProperties(registerAdminDTO, Admin.class);
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        adminMapper.insert(admin);

        // 新增该管理员的管理员角色权限
        personRoleMapper.saveUserRole(admin.getId(), RoleConstant.ADMIN_ROLE);

        log.info("管理员注册成功，ID：{}，用户名：{}", admin.getId(), admin.getUsername());
    }

    @Override
    public LoginResultVO login(LoginAdminDTO loginAdminDTO) {
        log.debug("开始管理员登录流程，用户名：{}", loginAdminDTO.getUsername());

        // 验证验证码是否正确
        VerificationCodeUtil.judgmentCode(loginAdminDTO.getCode(), loginAdminDTO.getKey());

        // 查询数据库中是否存在该账号
        Admin adminProfile = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, loginAdminDTO.getUsername()));
        if (adminProfile == null) {
            log.warn("管理员登录失败：账号不存在，用户名：{}", loginAdminDTO.getUsername());
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_AND_ACCOUNT_ERROR);
        }

        // 判断该账号状态是否处于正常状态
        if (!Objects.equals(adminProfile.getStatus(), LoginConstant.ACCOUNT_NORMAL)) {
            log.warn("管理员登录失败：账号状态异常，ID：{}，状态：{}", adminProfile.getId(), adminProfile.getStatus());
            throw new BusinessException(AuthenticationExceptionEnum.USER_BANNED);
        }

        // 密码校验
        boolean matches = passwordEncoder.matches(loginAdminDTO.getPassword(), adminProfile.getPassword());
        if (!matches) {
            log.warn("管理员登录失败：密码错误，用户名：{}", loginAdminDTO.getUsername());
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_AND_ACCOUNT_ERROR);
        }

        // 查询管理员权限
        List<String> permissions = permissionMapper.queryPermsByPersonId(adminProfile.getId());

        // 查询管理员角色
        List<String> roles = roleMapper.queryRoleByPersonId(adminProfile.getId());

        // 认证成功生成token，根据adminId生成token
        LoginUser loginUser = new LoginUser(adminProfile, permissions, roles);
        String token = JwtUtil.createJwtWithRedis(loginUser, true, JwtType.ADMIN);

        log.info("管理员登录成功，ID：{}，用户名：{}", adminProfile.getId(), adminProfile.getUsername());

        return LoginResultVO.builder()
                .token(token)
                .roles(roles)
                .build();
    }

    @Override
    public void logout() {
        LoginUser currentLoginUser = UserInfoThreadLocalUtil.getCurrentLoginUserThrow();
        Long adminId = currentLoginUser.getBasicsUser().getId();
        JwtUtil.deleteJwtWithRedis(adminId, JwtType.ADMIN);
        log.info("管理员登出成功，ID：{}", adminId);
    }

    @Override
    @Transactional
    public void pwdUpdate(PasswordDTO passwordDTO) {
        log.debug("开始管理员密码修改流程");

        // 获取当前管理员id
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        // 校验管理员原密码是否正确
        Admin admin = adminMapper.selectById(currentId);
        if (!passwordEncoder.matches(passwordDTO.getOriginalPassword(), admin.getPassword())) {
            log.warn("管理员密码修改失败：原密码错误，ID：{}", currentId);
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_ERROR);
        }

        // 新密码加密
        String encryptedNewPassword = passwordEncoder.encode(passwordDTO.getPassword());

        // 更新管理员密码
        UpdateWrapper<Admin> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().eq(Admin::getId, currentId).set(Admin::getPassword, encryptedNewPassword);
        adminMapper.update(updateWrapper);

        log.info("管理员密码修改成功，ID：{}", currentId);
    }
}
