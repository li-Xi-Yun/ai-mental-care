package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.constant.RoleConstant;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.user.AdminUserCreateDTO;
import org.lixiyun.pojo.dto.admin.user.AdminUserUpdateDTO;
import org.lixiyun.pojo.dto.admin.user.ResetPasswordDTO;
import org.lixiyun.pojo.dto.admin.user.UserQueryDTO;
import org.lixiyun.pojo.dto.admin.user.UserStatusDTO;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.pojo.tool.BasicsUser;
import org.lixiyun.pojo.vo.admin.user.UserVO;
import org.lixiyun.server.mapper.PersonRoleMapper;
import org.lixiyun.server.mapper.UserMapper;
import org.lixiyun.server.service.admin.AdminUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 管理员用户管理服务实现类
 * <p>
 * 提供普通用户的分页查询、详情、新增、编辑、状态修改、密码重置、注销等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-04-20 14:39
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final PersonRoleMapper personRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> listUsers(UserQueryDTO queryDTO) {
        log.info("用户列表查询，查询条件：{}", queryDTO);

        String loginAccount = queryDTO.getLoginAccount();
        String username = queryDTO.getUsername();
        String mobile = queryDTO.getMobile();
        Integer status = queryDTO.getStatus();

        // 构建分页对象
        Page<User> pageParam = new PageQuery(queryDTO.getPageSize(), queryDTO.getPageNum()).build();

        // 执行分页查询
        Page<User> userPage = userMapper.selectPage(pageParam, new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(loginAccount), User::getLoginAccount, loginAccount)
                .like(StringUtils.hasText(username), User::getUsername, username)
                .like(StringUtils.hasText(mobile), User::getMobile, mobile)
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getCreatedTime));

        log.info("用户列表查询完成，总数：{}", userPage.getTotal());
        return PageResult.convert(userPage, UserVO.class);
    }

    @Override
    public UserVO getUserDetail(Long id) {
        log.info("用户详情查询，用户id：{}", id);

        User user = getUserOrThrow(id);
        return BeanUtil.copyProperties(user, UserVO.class);
    }

    @Override
    @Transactional
    public void createUser(AdminUserCreateDTO createDTO) {
        log.info("管理端新增用户，账号名：{}", createDTO.getLoginAccount());

        // 校验账号名唯一性
        checkLoginAccountUniqueness(createDTO.getLoginAccount());

        User user = BeanUtil.copyProperties(createDTO, User.class);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        log.info("管理端新增用户成功，用户id：{}", user.getId());

        // 绑定普通用户角色
        personRoleMapper.saveUserRole(user.getId(), RoleConstant.USER_ROLE);
    }

    @Override
    @Transactional
    public void updateUser(Long id, AdminUserUpdateDTO updateDTO) {
        log.info("管理端编辑用户，用户id：{}", id);

        User currentUser = getUserOrThrow(id);

        // 账号名变更：仅在发生变化时校验唯一性并记录账号名修改时间
        if (StringUtils.hasText(updateDTO.getLoginAccount()) && !updateDTO.getLoginAccount().equals(currentUser.getLoginAccount())) {
            checkLoginAccountUniqueness(updateDTO.getLoginAccount());
        }

        User user = BeanUtil.copyProperties(updateDTO, User.class);
        user.setId(id);
        if (StringUtils.hasText(updateDTO.getLoginAccount()) && !updateDTO.getLoginAccount().equals(currentUser.getLoginAccount())) {
            user.setLoginAccountUpdateTime(LocalDateTime.now());
        }
        userMapper.updateById(user);
        log.info("管理端编辑用户成功，用户id：{}", id);
    }

    @Override
    @Transactional
    public void updateUserStatus(UserStatusDTO statusDTO) {
        log.info("用户状态修改，用户id：{}，状态：{}", statusDTO.getId(), statusDTO.getStatus());

        getUserOrThrow(statusDTO.getId());

        // 执行更新：封禁时记录封禁时间，其他状态清空封禁字段
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, statusDTO.getId())
                .set(User::getStatus, statusDTO.getStatus());
        if (statusDTO.getStatus() == BasicsUser.USER_STATUS_BAN) {
            updateWrapper.set(User::getBanTime, LocalDateTime.now());
            if (StringUtils.hasText(statusDTO.getBanReason())) {
                updateWrapper.set(User::getBanReason, statusDTO.getBanReason());
            }
            if (statusDTO.getBanEndTime() != null) {
                updateWrapper.set(User::getBanEndTime, statusDTO.getBanEndTime());
            }
        } else {
            clearBanFields(updateWrapper);
        }
        userMapper.update(null, updateWrapper);

        log.info("用户状态修改成功，用户id：{}", statusDTO.getId());
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetPasswordDTO resetPasswordDTO) {
        log.info("重置用户密码，用户id：{}", id);

        getUserOrThrow(id);

        // 新密码加密
        String encryptedNewPassword = passwordEncoder.encode(resetPasswordDTO.getNewPassword());

        // 更新密码
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getPassword, encryptedNewPassword));
        if (updated == 0) {
            log.error("重置用户密码失败，用户id：{}", id);
            throw new BusinessException(AuthenticationExceptionEnum.PASSWORD_RESET_FAILED);
        }
        log.info("重置用户密码成功，用户id：{}", id);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        log.info("用户注销，用户id：{}", id);

        getUserOrThrow(id);

        // 置为注销状态并清空封禁字段
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, id)
                .set(User::getStatus, BasicsUser.USER_STATUS_LOGOUT);
        clearBanFields(updateWrapper);
        userMapper.update(null, updateWrapper);
        log.info("用户注销成功，用户id：{}", id);
    }

    /**
     * 查询用户，不存在则抛出异常
     *
     * @param id 用户ID
     * @return 用户实体
     */
    private User getUserOrThrow(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            log.warn("用户不存在，用户id：{}", id);
            throw new BusinessException(AuthenticationExceptionEnum.USER_NOT_EXIST);
        }
        return user;
    }

    /**
     * 清空封禁字段
     *
     * @param updateWrapper 更新构造器
     */
    private void clearBanFields(LambdaUpdateWrapper<User> updateWrapper) {
        updateWrapper.set(User::getBanTime, null)
                .set(User::getBanEndTime, null)
                .set(User::getBanReason, null);
    }

    /**
     * 校验账号名是否唯一
     *
     * @param loginAccount 账号名
     */
    private void checkLoginAccountUniqueness(String loginAccount) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getLoginAccount, loginAccount));
        if (count > 0) {
            log.warn("账号名已存在：{}", loginAccount);
            throw new BusinessException(AuthenticationExceptionEnum.LOGIN_ACCOUNT_EXIST);
        }
    }

}