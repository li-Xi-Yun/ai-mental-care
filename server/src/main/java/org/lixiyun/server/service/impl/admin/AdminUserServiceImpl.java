package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.admin.user.AdminQueryDTO;
import org.lixiyun.pojo.dto.admin.user.AdminStatusDTO;
import org.lixiyun.pojo.dto.admin.user.UserQueryDTO;
import org.lixiyun.pojo.dto.admin.user.UserStatusDTO;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.pojo.tool.BasicsUser;
import org.lixiyun.pojo.vo.admin.user.AdminVO;
import org.lixiyun.pojo.vo.admin.user.UserVO;
import org.lixiyun.server.mapper.AdminMapper;
import org.lixiyun.server.mapper.UserMapper;
import org.lixiyun.server.service.admin.AdminUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 管理员用户管理服务实现类
 * <p>
 * 提供用户和管理员的查询、状态修改等功能
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
    private final AdminMapper adminMapper;

    @Override
    public PageResult<UserVO> listUsers(UserQueryDTO queryDTO) {
        log.debug("开始查询用户列表，查询条件: {}", queryDTO);

        // 构建分页对象
        Page<User> pageParam = new PageQuery(queryDTO.getPageSize(), queryDTO.getPageNum()).build();

        // 执行分页查询
        Page<User> userPage = userMapper.selectPage(pageParam, new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .eq(queryDTO.getUserId() != null, User::getId, queryDTO.getUserId())
                .like(StringUtils.hasText(queryDTO.getUsername()), User::getUsername, queryDTO.getUsername())
                .eq(queryDTO.getStatus() != null, User::getStatus, queryDTO.getStatus())
                .orderByDesc(User::getCreatedTime));

        // 转换为 VO 并返回分页结果
        PageResult<UserVO> result = PageResult.convert(userPage, UserVO.class);

        log.debug("用户列表查询完成，总数: {}, 当前页记录数: {}", result.getTotal(), result.getRecords().size());
        return result;
    }

    @Override
    public PageResult<AdminVO> listAdmins(AdminQueryDTO queryDTO) {
        log.debug("开始查询管理员列表，查询条件: {}", queryDTO);

        // 构建分页对象
        Page<Admin> pageParam = new PageQuery(queryDTO.getPageSize(), queryDTO.getPageNum()).build();

        // 执行分页查询
        Page<Admin> adminPage = adminMapper.selectPage(pageParam, new LambdaQueryWrapper<Admin>()
                .eq(Admin::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .eq(queryDTO.getAdminId() != null, Admin::getId, queryDTO.getAdminId())
                .like(StringUtils.hasText(queryDTO.getUsername()), Admin::getUsername, queryDTO.getUsername())
                .eq(queryDTO.getStatus() != null, Admin::getStatus, queryDTO.getStatus())
                .orderByDesc(Admin::getCreatedTime));

        // 转换为 VO 并返回分页结果
        PageResult<AdminVO> result = PageResult.convert(adminPage, AdminVO.class);

        log.debug("管理员列表查询完成，总数: {}, 当前页记录数: {}", result.getTotal(), result.getRecords().size());
        return result;
    }

    @Override
    @Transactional
    public void updateUserStatus(UserStatusDTO statusDTO) {
        log.info("开始修改用户状态，用户ID: {}, 状态: {}", statusDTO.getId(), statusDTO.getStatus());

        // 检查用户是否存在
        User temp = userMapper.selectById(statusDTO.getId());
        if (temp == null || temp.deletedFlat()) {
            log.error("用户不存在或已删除，用户ID: {}", statusDTO.getId());
            throw new BusinessException(AuthenticationExceptionEnum.USER_NOT_EXIST);
        }

        User user = BeanUtil.copyProperties(statusDTO, User.class);
        if(user.isBanned()){
            user.setBanTime(LocalDateTime.now());
        }
        if(statusDTO.getStatus() == BasicsUser.USER_STATUS_NORMAL){
            user.setBanTime(null);
        }

        // 执行更新
        int updated = userMapper.updateById(user);
        if (updated == 0) {
            log.error("用户状态修改失败，用户ID: {}", statusDTO.getId());
            throw new BusinessException(AuthenticationExceptionEnum.USER_STATUS_UPDATE_FAILED);
        }

        log.info("用户状态修改成功，用户ID: {}, 新状态: {}", statusDTO.getId(), statusDTO.getStatus());
    }

    @Override
    @Transactional
    public void updateAdminStatus(AdminStatusDTO statusDTO) {
        log.info("开始修改管理员状态，管理员ID: {}, 状态: {}", statusDTO.getId(), statusDTO.getStatus());

        // 检查管理员是否存在
        Admin temp = adminMapper.selectById(statusDTO.getId());
        if (temp == null || temp.getDeleted() != null && temp.getDeleted().equals(DeleteConstant.DELETE_FLAG_YES)) {
            log.error("管理员不存在或已删除，管理员ID: {}", statusDTO.getId());
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_NOT_FOUND);
        }

        Admin admin = BeanUtil.copyProperties(statusDTO, Admin.class);
        if(admin.isBanned()){
            admin.setBanTime(LocalDateTime.now());
        }

        // 执行更新
        int updated = adminMapper.updateById(admin);
        if (updated == 0) {
            log.error("管理员状态修改失败，管理员ID: {}", statusDTO.getId());
            throw new BusinessException(AuthenticationExceptionEnum.ADMIN_STATUS_UPDATE_FAILED);
        }

        log.info("管理员状态修改成功，管理员ID: {}, 新状态: {}", statusDTO.getId(), statusDTO.getStatus());
    }
}
