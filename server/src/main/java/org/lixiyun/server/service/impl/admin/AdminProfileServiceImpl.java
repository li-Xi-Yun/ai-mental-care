package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.pojo.dto.admin.profile.AdminProfileDTO;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.vo.admin.profile.AdminProfileVO;
import org.lixiyun.server.mapper.AdminMapper;
import org.lixiyun.server.mapper.RoleMapper;
import org.lixiyun.server.service.admin.AdminProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理员个人资料服务实现类
 * <p>
 * 提供管理员个人资料的更新、查询等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-04-18 20:43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProfileServiceImpl implements AdminProfileService {

    private final AdminMapper adminMapper;

    /**
     * 更新管理员资料
     * <p>
     * 根据传入的管理员资料信息更新管理员的个人资料，包括用户名、手机号、邮箱，
     * 账号名(login_account)不支持本人修改
     * </p>
     *
     * @param adminProfileDTO 管理员资料数据传输对象 {@link AdminProfileDTO}
     */
    @Override
    @Transactional
    public void updateProfile(AdminProfileDTO adminProfileDTO) {
        log.info("管理员资料更新，用户名：{}", adminProfileDTO.getUsername());

        // 获取当前管理员ID
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        Admin admin = BeanUtil.copyProperties(adminProfileDTO, Admin.class);

        // 执行更新操作
        adminMapper.update(admin, new LambdaUpdateWrapper<Admin>()
                .eq(Admin::getId, currentId));

        log.info("管理员资料更新成功，ID：{}", currentId);
    }

    @Override
    public AdminProfileVO getAdminProfile() {
        // 获取当前管理员ID
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.debug("管理员资料查询，ID：{}", currentId);

        // 查询管理员信息
        Admin admin = adminMapper.selectById(currentId);

        // 转换为VO对象

        return BeanUtil.copyProperties(admin, AdminProfileVO.class);
    }
}