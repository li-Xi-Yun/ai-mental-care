package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.pojo.dto.admin.profile.AdminProfileDTO;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.vo.admin.profile.AdminProfileVO;
import org.lixiyun.server.mapper.AdminMapper;
import org.lixiyun.server.service.admin.AdminProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * 根据传入的管理员资料信息更新管理员的个人资料，包括用户名、手机号、邮箱
     * </p>
     *
     * @param adminProfileDTO 管理员资料数据传输对象 {@link AdminProfileDTO}
     */
    @Override
    @Transactional
    public void updateProfile(AdminProfileDTO adminProfileDTO) {
        log.debug("开始更新管理员资料");

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
        log.debug("开始获取管理员资料");

        // 获取当前管理员ID
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        // 查询管理员信息
        Admin admin = adminMapper.selectById(currentId);

        // 转换为VO对象，将mobile字段映射为phone
        AdminProfileVO adminProfileVO = BeanUtil.copyProperties(admin, AdminProfileVO.class);

        log.info("获取管理员资料成功，ID：{}", currentId);

        return adminProfileVO;
    }
}
