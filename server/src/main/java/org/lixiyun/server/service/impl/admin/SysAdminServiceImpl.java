package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.constant.RoleConstant;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.constant.AdminConstant;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminAddDTO;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminQueryDTO;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminStatusUpdateDTO;
import org.lixiyun.pojo.dto.admin.sysadmin.AdminUpdateDTO;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.entity.permission.Role;
import org.lixiyun.pojo.tool.BasicsUser;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;
import org.lixiyun.pojo.vo.admin.sysadmin.AdminDetailVO;
import org.lixiyun.pojo.vo.admin.sysadmin.AdminPageVO;
import org.lixiyun.pojo.vo.admin.sysadmin.RoleVO;
import org.lixiyun.server.mapper.AdminMapper;
import org.lixiyun.server.mapper.PersonRoleMapper;
import org.lixiyun.server.mapper.PersonTempPermissionMapper;
import org.lixiyun.server.mapper.RoleMapper;
import org.lixiyun.server.service.admin.SysAdminService;
import org.lixiyun.server.service.admin.SysRoleService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
public class SysAdminServiceImpl implements SysAdminService {

    private final SysRoleService sysRoleService;
    private final AdminMapper adminMapper;
    private final RoleMapper roleMapper;
    private final PersonRoleMapper personRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PersonTempPermissionMapper personTempPermissionMapper;

    @Override
    public PageResult<AdminPageVO> pageAdminList(AdminQueryDTO queryDTO) {
        log.debug("系统管理员Service-开始分页查询管理员列表，查询条件: {}", queryDTO);

        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();
        String keyword = queryDTO.getKeyword();
        Integer status = queryDTO.getStatus();
        Integer deleted = queryDTO.getDeleted();

        PageHelper.startPage(pageNum, pageSize);
        List<AdminPageVO> adminPage = adminMapper.pageAdminListWithDeleted(keyword, status, deleted);

        PageInfo<AdminPageVO> page = new PageInfo<>(adminPage);

        PageResult<AdminPageVO> result = new PageResult<>(page.getTotal(), page.getList());

        log.debug("系统管理员Service-管理员列表查询完成，总数: {}, 当前页记录数: {}", result.getTotal(), result.getRecords().size());
        return result;
    }

    @Override
    public AdminDetailVO getAdminDetail(Long id) {
        log.debug("系统管理员Service-开始查询管理员详情，管理员ID: {}", id);

        AdminDetailVO adminDetailVO = adminMapper.getAdminDetailWithNames(id);

        if (adminDetailVO == null) {
            log.error("系统管理员Service-管理员不存在或已删除，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_NOT_FOUND);
        }

        log.debug("系统管理员Service-管理员详情查询完成，管理员ID: {}", id);
        return adminDetailVO;
    }

    @Override
    @Transactional
    public void addAdmin(AdminAddDTO adminAddDTO) {
        log.debug("系统管理员Service-开始新增管理员，用户名: {}", adminAddDTO.getUsername());

        String username = adminAddDTO.getUsername();
        String password = adminAddDTO.getPassword();
        String mobile = adminAddDTO.getMobile();
        String email = adminAddDTO.getEmail();
        Boolean sysPassword = adminAddDTO.getSysPassword();

        LambdaQueryWrapper<Admin> queryWrapper = new LambdaQueryWrapper<Admin>()
                .and(wrapper -> wrapper
                        .eq(StrUtil.isNotBlank(email), Admin::getEmail, email)
                        .or()
                        .eq(StrUtil.isNotBlank(mobile), Admin::getMobile, mobile));

        Admin existAdmin = adminMapper.selectOne(queryWrapper);
        if (existAdmin != null) {
            if (StrUtil.isNotBlank(email) && email.equals(existAdmin.getEmail())) {
                log.error("系统管理员Service-管理员邮箱已存在，邮箱: {}", email);
                throw new BusinessException(AuthenticationExceptionEnum.ADMIN_EMAIL_EXIST);
            }
            if (StrUtil.isNotBlank(mobile) && mobile.equals(existAdmin.getMobile())) {
                log.error("系统管理员Service-管理员手机号已存在，手机号: {}", mobile);
                throw new BusinessException(AuthenticationExceptionEnum.ADMIN_MOBILE_EXIST);
            }
        }


        Admin admin = Admin.builder()
                .username(username)
                .loginAccount(generateUniqueLoginAccount())
                .password(sysPassword == null ? passwordEncoder.encode(AdminConstant.DEFAULT_PASSWORD) : passwordEncoder.encode(password))
                .email(email)
                .mobile(mobile)
                .status(BasicsUser.USER_STATUS_NORMAL)
                .build();

        int inserted = adminMapper.insert(admin);
        if (inserted == 0) {
            log.error("系统管理员Service-管理员新增失败，用户名: {}", username);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_ADD_FAILED);
        }

        personRoleMapper.saveUserRole(admin.getId(), RoleConstant.ADMIN_ROLE);

        log.info("系统管理员Service-管理员新增成功，ID: {}, 用户名: {}", admin.getId(), username);
    }

    @Override
    @Transactional
    public void updateAdmin(AdminUpdateDTO adminUpdateDTO) {
        log.debug("系统管理员Service-开始修改管理员信息，管理员ID: {}", adminUpdateDTO.getId());

        Long id = adminUpdateDTO.getId();
        String username = adminUpdateDTO.getUsername();
        String mobile = adminUpdateDTO.getMobile();
        String email = adminUpdateDTO.getEmail();

        Admin existAdmin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getId, id));

        if (existAdmin == null) {
            log.error("系统管理员Service-管理员不存在或已删除，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_NOT_FOUND);
        }

        if (StrUtil.isNotBlank(username) && !username.equals(existAdmin.getUsername())) {
            Admin usernameExist = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                    .eq(Admin::getUsername, username));
            if (usernameExist != null) {
                log.error("系统管理员Service-管理员用户名已存在，用户名: {}", username);
                throw new BusinessException(AuthenticationExceptionEnum.ADMIN_USERNAME_EXIST);
            }
        }

        if (StrUtil.isNotBlank(email) && !email.equals(existAdmin.getEmail())) {
            Admin emailExist = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                    .eq(Admin::getEmail, email));
            if (emailExist != null) {
                log.error("系统管理员Service-管理员邮箱已存在，邮箱: {}", email);
                throw new BusinessException(AuthenticationExceptionEnum.ADMIN_EMAIL_EXIST);
            }
        }

        if (StrUtil.isNotBlank(mobile) && !mobile.equals(existAdmin.getMobile())) {
            Admin mobileExist = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                    .eq(Admin::getMobile, mobile));
            if (mobileExist != null) {
                log.error("系统管理员Service-管理员手机号已存在，手机号: {}", mobile);
                throw new BusinessException(AuthenticationExceptionEnum.ADMIN_MOBILE_EXIST);
            }
        }

        Admin admin = new Admin();
        admin.setId(id);
        admin.setUsername(username);
        admin.setMobile(mobile);
        admin.setEmail(email);

        int updated = adminMapper.updateById(admin);
        if (updated == 0) {
            log.error("系统管理员Service-管理员信息修改失败，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_UPDATE_FAILED);
        }

        log.info("系统管理员Service-管理员信息修改成功，管理员ID: {}", id);
    }

    @Override
    public void updateAdminStatus(Long id, AdminStatusUpdateDTO statusDTO) {
        log.debug("系统管理员Service-开始修改管理员状态，管理员ID: {}, 状态: {}", id, statusDTO.getStatus());

        Integer status = statusDTO.getStatus();
        LocalDateTime banTime = statusDTO.getBanTime();
        LocalDateTime banEndTime = statusDTO.getBanEndTime();
        String banReason = statusDTO.getBanReason();

        Admin existAdmin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getId, id));

        if (existAdmin == null) {
            log.error("系统管理员Service-管理员不存在或已删除，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_NOT_FOUND);
        }

        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        if (currentId.equals(id)) {
            throw new BusinessException(AuthenticationExceptionEnum.CAN_NOT_BAN_SELF);
        }

        Admin admin = new Admin();
        admin.setId(id);
        admin.setStatus(status);

        if (Objects.equals(status, BasicsUser.USER_STATUS_BAN)) {
            admin.setBanTime(banTime != null ? banTime : LocalDateTime.now());
            admin.setBanEndTime(banEndTime);
            LocalDateTime now = LocalDateTime.now();
            if(banEndTime != null){
                if(banEndTime.isBefore(now)){
                    throw new BusinessException(AuthenticationExceptionEnum.USER_BAN_END_TIME_INVALID);
                }
                // banTime为空则自动赋值now；不为空则校验时间顺序
                LocalDateTime realBanTime = banTime != null ? banTime : now;
                if(realBanTime.isAfter(banEndTime)){
                    throw new BusinessException(AuthenticationExceptionEnum.USER_BAN_END_TIME_INVALID);
                }
            }
            admin.setBanReason(banReason);
        } else if (Objects.equals(status, BasicsUser.USER_STATUS_NORMAL)) {
            admin.setBanTime(null);
            admin.setBanEndTime(null);
            admin.setBanReason("");
        }

        int updated = adminMapper.updateById(admin);
        if (updated == 0) {
            log.error("系统管理员Service-管理员状态修改失败，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_STATUS_UPDATE_FAILED);
        }

        if(!admin.isNormal()){
            JwtUtil.deleteJwtWithRedis(id, JwtType.ADMIN);
        }
        log.info("系统管理员Service-管理员状态修改成功，管理员ID: {}, 新状态: {}", id, status);
    }

    @Override
    public void resetAdminPassword(Long id) {
        log.debug("系统管理员Service-开始重置管理员密码，管理员ID: {}", id);

        Admin existAdmin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getId, id));

        if (existAdmin == null) {
            log.error("系统管理员Service-管理员不存在或已删除，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_NOT_FOUND);
        }

        Admin admin = Admin.builder()
                .id(id)
                .password(passwordEncoder.encode(AdminConstant.DEFAULT_PASSWORD))
                .build();

        int updated = adminMapper.updateById(admin);
        if (updated == 0) {
            log.error("系统管理员Service-管理员密码重置失败，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_PASSWORD_RESET_FAILED);
        }

        JwtUtil.deleteJwtWithRedis(id, JwtType.ADMIN);

        log.info("系统管理员Service-管理员密码重置成功，管理员ID: {}", id);
    }

    @Override
    public void deleteAdmin(Long id) {
        log.debug("系统管理员Service-开始删除管理员，管理员ID: {}", id);

        Admin existAdmin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getId, id));

        if (existAdmin == null) {
            log.error("系统管理员Service-管理员不存在或已删除，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_NOT_FOUND);
        }

        int updated = adminMapper.deleteById(id);
        if (updated == 0) {
            log.error("系统管理员Service-管理员删除失败，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_DELETE_FAILED);
        }

        JwtUtil.deleteJwtWithRedis(id, JwtType.ADMIN);

        log.info("系统管理员Service-管理员删除成功，管理员ID: {}", id);
    }

    @Override
    public List<RoleVO> getAdminRoles(Long id) {
        log.debug("系统管理员Service-开始查询管理员角色列表，管理员ID: {}", id);

        List<Role> roles = roleMapper.queryRoleDetailByPersonId(id);

        log.debug("系统管理员Service-管理员角色列表查询完成，管理员ID: {}, 角色数量: {}", id, roles.size());
        return StreamUtils.toListVO(roles, RoleVO.class);
    }

    @Override
    @Transactional
    public void assignRolesToAdmin(Long id, List<Long> roleIdList) {
        log.debug("系统管理员Service-开始给管理员分配角色，管理员ID: {}, 角色ID列表: {}", id, roleIdList);

        Admin existAdmin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getId, id));

        if (existAdmin == null) {
            log.error("系统管理员Service-管理员不存在或已删除，管理员ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_NOT_FOUND);
        }

        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        if(Objects.equals(currentId, id)){
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_ROLE_ASSIGN_FAILED);
        }

        if (!sysRoleService.assignRoles(id, roleIdList)) {
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_ROLE_ASSIGN_FAILED);
        }

        JwtUtil.deleteJwtWithRedis(id, JwtType.ADMIN);

        log.info("系统管理员Service-管理员角色分配成功，管理员ID: {}, 分配角色数量: {}", id, roleIdList.size());
    }

    @Override
    public List<TempPermissionVO> getAdminTempPermissions(Long id, Integer status) {
        log.debug("系统管理员Service-开始查询管理员临时权限记录，管理员ID: {},状态: {}", id, status);

        List<TempPermissionVO> tempPermissions = personTempPermissionMapper.pageTempPermissionList(id, status);

        log.debug("系统管理员Service-管理员临时权限记录查询完成，管理员ID: {},状态: {}, 记录数量: {}", id, status, tempPermissions.size());
        return tempPermissions;
    }

    private String generateUniqueLoginAccount() {
        String loginAccount;
        int maxAttempts = 100;
        int attempt = 0;

        do {
            loginAccount = RandomUtil.randomString(32);
            attempt++;

            if (attempt >= maxAttempts) {
                log.error("生成唯一账号名失败，已尝试{}次", maxAttempts);
                throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
            }
        } while (existsLoginAccount(loginAccount));

        return loginAccount;
    }

    private boolean existsLoginAccount(String loginAccount) {
        Long count = adminMapper.selectCount(
                new LambdaQueryWrapper<Admin>().eq(Admin::getLoginAccount, loginAccount)
        );
        return count != null && count > 0;
    }
}