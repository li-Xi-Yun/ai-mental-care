package org.lixiyun.server.service.user;

import org.lixiyun.pojo.dto.user.user.UserProfileDTO;
import org.lixiyun.pojo.vo.user.user.UserProfileVO;

/**
 * 用户资料服务接口
 * <p>
 * 提供用户个人资料管理相关的核心业务功能，包括用户资料更新、获取用户资料、
 * 获取用户社交统计信息、用户标签管理等操作
 * </p>
 */
public interface ProfileService {

    /**
     * 更新用户资料
     * <p>
     * 根据传入的用户资料信息更新用户的个人资料
     * </p>
     *
     * @param userProfileDTO 用户资料数据传输对象，包含需要更新的用户信息 {@link UserProfileDTO}
     */
    void updateProfile(UserProfileDTO userProfileDTO);

    /**
     * 获取用户资料
     * <p>
     * 根据用户ID获取用户的详细个人资料信息
     * </p>
     *
     * @return 用户资料视图对象，包含用户的详细信息 {@link UserProfileVO}
     */
    UserProfileVO getUserProfile();

}