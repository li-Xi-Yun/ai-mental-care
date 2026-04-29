package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.profile.AdminProfileDTO;
import org.lixiyun.pojo.vo.admin.profile.AdminProfileVO;

/**
 * 管理员个人资料服务接口
 * <p>
 * 提供管理员个人资料的更新、查询等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-04-18 20:43
 */
public interface AdminProfileService {

    /**
     * 更新管理员资料
     * <p>
     * 根据传入的管理员资料信息更新管理员的个人资料
     * </p>
     *
     * @param adminProfileDTO 管理员资料数据传输对象，包含需要更新的管理员信息 {@link AdminProfileDTO}
     */
    void updateProfile(AdminProfileDTO adminProfileDTO);

    /**
     * 获取管理员资料
     * <p>
     * 根据当前登录管理员ID获取管理员的详细个人资料信息
     * </p>
     *
     * @return 管理员资料视图对象，包含管理员的详细信息 {@link AdminProfileVO}
     */
    AdminProfileVO getAdminProfile();
}
