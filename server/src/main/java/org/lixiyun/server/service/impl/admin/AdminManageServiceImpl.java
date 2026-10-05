package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.constant.RoleConstant;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.common.verification.code.utils.VerificationCodeUtil;
import org.lixiyun.pojo.dto.admin.admin.RegisterAdminDTO;
import org.lixiyun.pojo.dto.admin.user.AdminQueryDTO;
import org.lixiyun.pojo.dto.admin.user.AdminStatusDTO;
import org.lixiyun.pojo.dto.admin.user.AdminUpdateDTO;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.entity.permission.PersonRole;
import org.lixiyun.pojo.tool.BasicsUser;
import org.lixiyun.pojo.vo.admin.user.AdminVO;
import org.lixiyun.server.mapper.AdminMapper;
import org.lixiyun.server.mapper.PersonRoleMapper;
import org.lixiyun.server.service.admin.AdminManageService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 管理员管理服务实现类
 * <p>
 * 提供管理员的新增、分页查询、详情、编辑（含角色调整）、状态修改、注销等功能，
 * 该模块为超级管理员专属操作
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminManageServiceImpl implements AdminManageService {

    /** 合法的管理员角色集合 */
    private static final Set<String> VALID_ADMIN_ROLES = Set.of(RoleConstant.ADMIN_ROLE, RoleConstant.SUPER_ADMIN_ROLE);

    private final AdminMapper adminMapper;
    private final PersonRoleMapper personRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void registerAdmin(RegisterAdminDTO registerAdminDTO) {
        // 仅超级管理员可新增管理员
        checkSuperAdmin();
        log.info("新增管理员，用户名：{}", registerAdminDTO.getUsername());

        // 校验验证码是否正确
        VerificationCodeUtil.judgmentCode(registerAdminDTO.getCode(), registerAdminDTO.getKey());

        // 账号名由系统自动生成
        String loginAccount = generateUniqueLoginAccount();

        Admin admin = BeanUtil.copyProperties(registerAdminDTO, Admin.class);
        admin.setLoginAccount(loginAccount);
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        adminMapper.insert(admin);
        log.info("新增管理员成功，管理员id：{}，账号名：{}", admin.getId(), loginAccount);

        // 绑定普通管理员角色
        personRoleMapper.saveUserRole(admin.getId(), RoleConstant.ADMIN_ROLE);
    }

    @Override
    public PageResult<AdminVO> listAdmins(AdminQueryDTO queryDTO) {
        // 仅超级管理员可查看管理员列表
        checkSuperAdmin();
        log.info("管理员列表查询，查询条件：{}", queryDTO);

        Page<AdminVO> pageParam = new PageQuery(queryDTO.getPageSize(), queryDTO.getPageNum()).build();
        IPage<AdminVO> adminPage = adminMapper.selectAdminPage(pageParam, queryDTO);

        log.info("管理员列表查询完成，总数：{}", adminPage.getTotal());
        return PageResult.convert(adminPage, AdminVO.class);
    }

    @Override
    public AdminVO getAdminDetail(Long id) {
        // 仅超级管理员可查看管理员详情
        checkSuperAdmin();
        log.info("管理员详情查询，管理员id：{}", id);

        getAdminOrThrow(id);
        return adminMapper.selectAdminVOById(id);
    }

    @Override
    @Transactional
    public void updateAdmin(Long id, AdminUpdateDTO updateDTO) {
        // 仅超级管理员可编辑管理员
        checkSuperAdmin();
        log.info("管理员编辑，管理员id：{}", id);

        Admin admin = getAdminOrThrow(id);

        // 账号名变更：校验唯一性并记录账号名修改时间
        if (StringUtils.hasText(updateDTO.getLoginAccount()) && !updateDTO.getLoginAccount().equals(admin.getLoginAccount())) {
            checkLoginAccountUniqueness(updateDTO.getLoginAccount());
            admin.setLoginAccount(updateDTO.getLoginAccount());
            admin.setLoginAccountUpdateTime(LocalDateTime.now());
        }

        // 基本信息更新
        if (StringUtils.hasText(updateDTO.getUsername())) {
            admin.setUsername(updateDTO.getUsername());
        }
        if (StringUtils.hasText(updateDTO.getMobile())) {
            admin.setMobile(updateDTO.getMobile());
        }
        if (StringUtils.hasText(updateDTO.getEmail())) {
            admin.setEmail(updateDTO.getEmail());
        }
        adminMapper.updateById(admin);

        // 角色调整
        if (StringUtils.hasText(updateDTO.getRole())) {
            updateAdminRole(id, updateDTO.getRole());
        }
        log.info("管理员编辑成功，管理员id：{}", id);
    }

    @Override
    @Transactional
    public void updateAdminStatus(AdminStatusDTO statusDTO) {
        // 仅超级管理员可修改管理员状态
        checkSuperAdmin();
        log.info("管理员状态修改，管理员id：{}，状态：{}", statusDTO.getId(), statusDTO.getStatus());

        getAdminOrThrow(statusDTO.getId());

        // 执行更新：封禁时记录封禁时间，其他状态清空封禁字段
        LambdaUpdateWrapper<Admin> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Admin::getId, statusDTO.getId())
                .set(Admin::getStatus, statusDTO.getStatus());
        if (statusDTO.getStatus() == BasicsUser.USER_STATUS_BAN) {
            updateWrapper.set(Admin::getBanTime, LocalDateTime.now());
            if (StringUtils.hasText(statusDTO.getBanReason())) {
                updateWrapper.set(Admin::getBanReason, statusDTO.getBanReason());
            }
            if (statusDTO.getBanEndTime() != null) {
                updateWrapper.set(Admin::getBanEndTime, statusDTO.getBanEndTime());
            }
        } else {
            updateWrapper.set(Admin::getBanTime, null)
                    .set(Admin::getBanEndTime, null)
                    .set(Admin::getBanReason, null);
        }
        adminMapper.update(null, updateWrapper);

        log.info("管理员状态修改成功，管理员id：{}", statusDTO.getId());
    }

    @Override
    @Transactional
    public void deleteAdmin(Long id) {
        // 仅超级管理员可注销管理员
        checkSuperAdmin();
        log.info("管理员注销，管理员id：{}", id);

        // 不能注销自己
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        if (currentId.equals(id)) {
            log.warn("不能注销自己的账号，管理员id：{}", id);
            throw new BusinessException(AuthenticationExceptionEnum.SUPER_ADMIN_ONLY);
        }

        getAdminOrThrow(id);

        // 置为注销状态并清空封禁字段
        LambdaUpdateWrapper<Admin> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Admin::getId, id)
                .set(Admin::getStatus, BasicsUser.USER_STATUS_LOGOUT)
                .set(Admin::getBanTime, null)
                .set(Admin::getBanEndTime, null)
                .set(Admin::getBanReason, null);
        adminMapper.update(null, updateWrapper);
        log.info("管理员注销成功，管理员id：{}", id);
    }

    /**
     * 调整管理员角色：删除旧角色关联并绑定新角色
     *
     * @param adminId 管理员ID
     * @param role    目标角色
     */
    private void updateAdminRole(Long adminId, String role) {
        if (!VALID_ADMIN_ROLES.contains(role)) {
            log.warn("非法管理员角色：{}", role);
            throw new BusinessException(AuthenticationExceptionEnum.ROLE_UPDATE_FAILED);
        }
        // 删除旧角色关联
        personRoleMapper.delete(new LambdaQueryWrapper<PersonRole>()
                .eq(PersonRole::getPersonId, adminId));
        // 绑定新角色
        personRoleMapper.saveUserRole(adminId, role);
    }

    /**
     * 系统自动生成唯一账号名
     *
     * @return 唯一账号名
     */
    private String generateUniqueLoginAccount() {
        String loginAccount;
        do {
            loginAccount = "admin_" + RandomUtil.randomString(6);
        } while (isLoginAccountExist(loginAccount));
        return loginAccount;
    }

    /**
     * 校验账号名是否已被占用
     *
     * @param loginAccount 账号名
     * @return true=已占用
     */
    private boolean isLoginAccountExist(String loginAccount) {
        Long count = adminMapper.selectCount(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getLoginAccount, loginAccount));
        return count > 0;
    }

    /**
     * 校验账号名唯一性
     *
     * @param loginAccount 账号名
     */
    private void checkLoginAccountUniqueness(String loginAccount) {
        if (isLoginAccountExist(loginAccount)) {
            log.warn("账号名已存在：{}", loginAccount);
            throw new BusinessException(AuthenticationExceptionEnum.LOGIN_ACCOUNT_EXIST);
        }
    }

    /**
     * 查询管理员，不存在则抛出异常
     *
     * @param id 管理员ID
     * @return 管理员实体
     */
    private Admin getAdminOrThrow(Long id) {
        Admin admin = adminMapper.selectById(id);
        if (admin == null) {
            log.warn("管理员不存在，管理员id：{}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_NOT_FOUND);
        }
        return admin;
    }

    /**
     * 校验当前登录用户是否为超级管理员
     * <p>兼容经过SpringSecurity转换后的 ROLE_ 前缀角色名</p>
     */
    private void checkSuperAdmin() {
        List<String> roles = UserInfoThreadLocalUtil.getUserRolesThrow();
        boolean isSuperAdmin = roles.stream()
                .anyMatch(role -> role.equals(RoleConstant.SUPER_ADMIN_ROLE)
                        || role.equals("ROLE_" + RoleConstant.SUPER_ADMIN_ROLE));
        if (!isSuperAdmin) {
            log.warn("非超级管理员尝试执行管理员管理操作，当前角色：{}", roles);
            throw new BusinessException(AuthenticationExceptionEnum.SUPER_ADMIN_ONLY);
        }
    }

}