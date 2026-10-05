package org.lixiyun.server.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.verification.code.utils.VerificationCodeUtil;
import org.lixiyun.pojo.dto.admin.admin.LoginAdminDTO;
import org.lixiyun.pojo.dto.user.user.PasswordDTO;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.tool.LoginUser;
import org.lixiyun.pojo.vo.user.user.LoginResultVO;
import org.lixiyun.pojo.vo.user.user.LoginUserInfoVO;
import org.lixiyun.server.mapper.AdminMapper;
import org.lixiyun.server.mapper.PermissionMapper;
import org.lixiyun.server.mapper.RoleMapper;
import org.lixiyun.server.service.admin.AdminAccountService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理员账号服务实现类
 * <p>
 * 提供管理员登录、登出、密码修改等功能
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

    @Override
    public LoginResultVO login(LoginAdminDTO loginAdminDTO) {
        log.info("管理员登录，账号名：{}", loginAdminDTO.getLoginAccount());

        // 验证验证码是否正确
        VerificationCodeUtil.judgmentCode(loginAdminDTO.getCode(), loginAdminDTO.getKey());

        // 查询数据库中是否存在该账号
        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getLoginAccount, loginAdminDTO.getLoginAccount()));
        if (admin == null) {
            log.warn("管理员登录失败：账号不存在，账号名：{}", loginAdminDTO.getLoginAccount());
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_AND_ACCOUNT_ERROR);
        }

        // 判断该账号状态是否处于正常状态
        if (!admin.isNormal()) {
            log.warn("管理员登录失败：账号状态异常，ID：{}，状态：{}", admin.getId(), admin.getStatus());
            throw new BusinessException(AuthenticationExceptionEnum.USER_BANNED);
        }

        // 密码校验
        boolean matches = passwordEncoder.matches(loginAdminDTO.getPassword(), admin.getPassword());
        if (!matches) {
            log.warn("管理员登录失败：密码错误，账号名：{}", loginAdminDTO.getLoginAccount());
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_AND_ACCOUNT_ERROR);
        }

        // 查询管理员权限
        List<String> permissions = permissionMapper.queryPermsByPersonId(admin.getId());

        // 查询管理员角色
        List<String> roles = roleMapper.queryRoleKeysByPersonId(admin.getId());

        // 认证成功生成token，根据adminId生成token
        LoginUser loginUser = new LoginUser(admin, permissions, roles);
        String token = JwtUtil.createJwtWithRedis(loginUser, true, JwtType.ADMIN);

        // 组装登录返回结果：token + 角色 + 管理员简要信息（含角色）
        LoginUserInfoVO userInfo = LoginUserInfoVO.builder()
                .id(admin.getId())
                .loginAccount(admin.getLoginAccount())
                .username(admin.getUsername())
                .build();

        log.info("管理员登录成功，ID：{}，账号名：{}", admin.getId(), admin.getLoginAccount());

        return LoginResultVO.builder()
                .token(token)
                .roles(roles)
                .userInfo(userInfo)
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
    public void pwdUpdate(PasswordDTO passwordDTO) {
        log.info("管理员密码修改");

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