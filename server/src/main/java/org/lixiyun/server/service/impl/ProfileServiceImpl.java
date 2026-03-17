package org.lixiyun.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.pojo.dto.user.UserProfileDTO;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.pojo.vo.user.UserProfileVO;
import org.lixiyun.server.mapper.UserMapper;
import org.lixiyun.server.service.ProfileService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;

    @Override
    public void updateProfile(UserProfileDTO userProfileDTO) {
        User user = BeanUtil.copyProperties(userProfileDTO, User.class);
        user.setId(UserInfoThreadLocalUtil.getCurrentIdThrow());
        userMapper.updateById(user);
    }

    @Override
    public UserProfileVO getUserProfile() {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        User user = userMapper.selectById(userId);
        return BeanUtil.copyProperties(user, UserProfileVO.class);
    }

}
