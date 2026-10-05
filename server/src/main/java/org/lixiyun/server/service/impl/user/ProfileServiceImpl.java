package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.constant.AccountConstant;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.dto.user.user.UserProfileDTO;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.pojo.vo.user.user.UserProfileVO;
import org.lixiyun.server.mapper.UserMapper;
import org.lixiyun.server.service.user.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;

    @Override
    public void updateProfile(UserProfileDTO userProfileDTO) {
        log.info("用户资料更新，账号名：{}", userProfileDTO.getLoginAccount());

        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        User user = BeanUtil.copyProperties(userProfileDTO, User.class);
        user.setId(userId);

        // 账号名变更处理：校验唯一性 + 180天修改间隔
        handleLoginAccountUpdate(userId, userProfileDTO.getLoginAccount());

        userMapper.updateById(user);
        log.info("用户资料更新成功，用户id：{}", userId);
    }

    @Override
    public UserProfileVO getUserProfile() {
        Long userId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        User user = userMapper.selectById(userId);
        return BeanUtil.copyProperties(user, UserProfileVO.class);
    }

    /**
     * 账号名变更处理：校验唯一性与180天修改间隔
     * <p>仅在传入新的账号名时执行，不传则忽略</p>
     *
     * @param userId        当前用户id
     * @param loginAccount  新的账号名，可为null
     */
    private void handleLoginAccountUpdate(Long userId, String loginAccount) {
        if (!StringUtils.hasText(loginAccount)) {
            return;
        }
        User currentUser = userMapper.selectById(userId);
        if (loginAccount.equals(currentUser.getLoginAccount())) {
            return;
        }

        // 校验账号名唯一性
        checkLoginAccountUniqueness(loginAccount);

        // 校验180天修改间隔
        LocalDateTime lastUpdateTime = currentUser.getLoginAccountUpdateTime();
        if (lastUpdateTime != null) {
            long intervalDays = java.time.temporal.ChronoUnit.DAYS.between(lastUpdateTime, LocalDateTime.now());
            if (intervalDays < AccountConstant.LOGIN_ACCOUNT_UPDATE_INTERVAL_DAYS) {
                log.warn("账号名修改过于频繁，用户id：{}，距上次修改天数：{}", userId, intervalDays);
                throw new BusinessException(AuthenticationExceptionEnum.LOGIN_ACCOUNT_UPDATE_TOO_FREQUENT);
            }
        }

        // 更新账号名与修改时间
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda()
                .eq(User::getId, userId)
                .set(User::getLoginAccount, loginAccount)
                .set(User::getLoginAccountUpdateTime, LocalDateTime.now());
        userMapper.update(updateWrapper);
    }

    /**
     * 校验其他用户是否已占用该账号名
     *
     * @param loginAccount 账号名
     */
    private void checkLoginAccountUniqueness(String loginAccount) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(User::getLoginAccount, loginAccount);
        if (userMapper.selectCount(queryWrapper) > 0) {
            log.warn("账号名已存在：{}", loginAccount);
            throw new BusinessException(AuthenticationExceptionEnum.LOGIN_ACCOUNT_EXIST);
        }
    }

}