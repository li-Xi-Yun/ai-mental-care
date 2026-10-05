package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.common.core.utils.StringUtils;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.sysuser.SysUserQueryDTO;
import org.lixiyun.pojo.dto.admin.sysuser.SysUserStatusUpdateDTO;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.pojo.entity.permission.Role;
import org.lixiyun.pojo.tool.BasicsUser;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;
import org.lixiyun.pojo.vo.admin.sysadmin.RoleVO;
import org.lixiyun.pojo.vo.admin.sysuser.SysUserDetailVO;
import org.lixiyun.pojo.vo.admin.sysuser.SysUserPageVO;
import org.lixiyun.server.mapper.PersonTempPermissionMapper;
import org.lixiyun.server.mapper.RoleMapper;
import org.lixiyun.server.mapper.UserMapper;
import org.lixiyun.server.service.admin.SysRoleService;
import org.lixiyun.server.service.admin.SysUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
public class SysUserServiceImpl implements SysUserService {

    private final SysRoleService sysRoleService;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PersonTempPermissionMapper personTempPermissionMapper;

    @Override
    public PageResult<SysUserPageVO> pageUserList(SysUserQueryDTO queryDTO) {
        log.debug("系统用户Service-开始分页查询用户列表，查询条件: {}", queryDTO);

        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();
        String keyword = queryDTO.getKeyword();
        Integer status = queryDTO.getStatus();

        Page<User> build = new PageQuery(pageSize, pageNum).build();
        Page<User> userPage = userMapper.selectPage(build, new LambdaQueryWrapper<User>()
                .eq(status != null, User::getStatus, status)
                .and(StringUtils.isNotBlank(keyword), wrapper -> wrapper
                        .eq(User::getLoginAccount, keyword)
                        .or().like(User::getUsername, keyword)
                        .or().likeRight(User::getMobile, keyword)
                        .or().likeRight(User::getEmail, keyword))
                .orderByDesc(User::getCreatedTime)
        );


        log.debug("系统用户Service-用户列表查询完成，总数: {}, 当前页记录数: {}", userPage.getTotal(), userPage.getSize());
        return PageResult.convert(userPage, SysUserPageVO.class);
    }

    @Override
    public SysUserDetailVO getUserDetail(Long id) {
        log.debug("系统用户Service-开始查询用户详情，用户ID: {}", id);

        User user = userMapper.selectById(id);

        if (user == null) {
            log.error("系统用户Service-用户不存在，用户ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.USER_NOT_FOUND);
        }

        log.debug("系统用户Service-用户详情查询完成，用户ID: {}", id);
        return BeanUtil.copyProperties(user, SysUserDetailVO.class);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long id, SysUserStatusUpdateDTO statusDTO) {
        log.debug("系统用户Service-开始修改用户状态，用户ID: {}, 状态信息: {}", id, statusDTO);

        Integer status = statusDTO.getStatus();
        String banReason = statusDTO.getBanReason();
        LocalDateTime banEndTime = statusDTO.getBanEndTime();
        LocalDateTime banTime = statusDTO.getBanTime();

        User user = userMapper.selectById(id);
        if (user == null) {
            log.error("系统用户Service-用户不存在，用户ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.USER_NOT_FOUND);
        }

        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, id);

        if (Objects.equals(status, BasicsUser.USER_STATUS_BAN)) {
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
            updateWrapper.set(User::getStatus, status)
                    .set(User::getBanReason, banReason)
                    .set(User::getBanTime, banTime != null ? banTime : now)
                    .set(User::getBanEndTime, banEndTime);
        } else if (Objects.equals(status, BasicsUser.USER_STATUS_NORMAL)) {
            updateWrapper.set(User::getStatus, status)
                    .set(User::getBanReason, null)
                    .set(User::getBanTime, null)
                    .set(User::getBanEndTime, null);
        }

        int updated = userMapper.update(null, updateWrapper);
        if (updated == 0) {
            log.error("系统用户Service-用户状态修改失败，用户ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.USER_STATUS_UPDATE_FAILED);
        }

        if(!Objects.equals(status, BasicsUser.USER_STATUS_NORMAL)){
            JwtUtil.deleteJwtWithRedis(id, JwtType.USER);
        }

        log.info("系统用户Service-用户状态修改成功，用户ID: {}, 新状态: {}", id, status);
    }

    @Override
    public List<RoleVO> getUserRoles(Long id) {
        log.debug("系统用户Service-开始查询用户角色列表，用户ID: {}", id);

        List<Role> roles = roleMapper.queryRoleDetailByPersonId(id);

        log.debug("系统用户Service-用户角色列表查询完成，用户ID: {}, 角色数量: {}", id, roles.size());
        return StreamUtils.toListVO(roles, RoleVO.class);
    }

    @Override
    @Transactional
    public void assignRolesToUser(Long id, List<Long> roleIdList) {
        log.debug("系统用户Service-开始给用户分配角色，用户ID: {}, 角色ID列表: {}", id, roleIdList);

        User user = userMapper.selectById(id);
        if (user == null) {
            log.error("系统用户Service-用户不存在，用户ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.USER_NOT_FOUND);
        }

        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        if (currentId.equals(id)) {
            throw new BusinessException(AuthenticationExceptionEnum.USER_CANT_ASSIGN_ROLE_TO_SELF);
        }

        if (!sysRoleService.assignRoles(id, roleIdList)) {
            throw new BusinessException(AuthenticationExceptionEnum.USER_ROLE_ASSIGN_FAILED);
        }

        JwtUtil.deleteJwtWithRedis(id, JwtType.USER);

        log.info("系统用户Service-用户角色分配成功，用户ID: {}, 分配角色数量: {}", id, roleIdList.size());
    }

    @Override
    public List<TempPermissionVO> getUserTempPermissions(Long id, Integer status) {
        log.debug("系统用户Service-开始查询用户临时权限记录，用户ID: {},状态: {}", id, status);

        List<TempPermissionVO> tempPermissions = personTempPermissionMapper.pageTempPermissionList(id, status);

        log.debug("系统用户Service-用户临时权限记录查询完成，用户ID: {},状态: {}, 记录数量: {}", id, status, tempPermissions.size());
        return tempPermissions;
    }
}