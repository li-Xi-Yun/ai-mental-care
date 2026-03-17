package org.lixiyun.server.login.impl;

import org.lixiyun.pojo.tool.LoginUser;
import org.lixiyun.server.login.OAuth2UserConverter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 这个是Github的转换器
 */
@Component
public class GithubUserConverter implements OAuth2UserConverter {
    @Override
    public LoginUser convert(Map<String, Object> attributes) {
        return null;
    }
//
//    @Autowired
//    private ProfileMapper profileMapper;
//
//    @Autowired
//    private MenuMapper menuMapper;
//
//    @Autowired
//    private AccountMapper accountMapper;
//
//    @Autowired
//    private AuthMapper authMapper;
//
//    @Autowired
//    private TransactionTemplate transactionTemplate;
//
//    @Override
//    public LoginUser convert(Map<String, Object> attributes) {
//        Integer authId = (Integer) attributes.get("id");
//
//        UserProfile userProfile = profileMapper.selectByAuthId(authId);
//
//        if (userProfile != null) {
//            // 用户已经进行过登录, 直接返回本地用户信息
//            BasicsUser basicsUser = new BasicsUser();
//            basicsUser.setUserId(userProfile.getUserId());
//            basicsUser.setStatus(accountMapper.selectById(userProfile.getUserId()).getStatus());
//
//            List<String> permissions = menuMapper.selectPermsByUserId(userProfile.getUserId());
//            List<String> roles = menuMapper.selectRoleByUserId(userProfile.getUserId());
//            return new LoginUser(basicsUser, permissions, roles, attributes);
//        }
//
//        // 用户未进行过登录, 创建一个用户
//
//        String username = (String) attributes.get("name");
//        String avatar = (String) attributes.get("avatar_url");
//        String email = (String) attributes.get("email");
//        String signature = (String) attributes.get("bio");
//        String region = (String) attributes.get("location");
//
//        if(username.length() >= UserProfile.USERNAME_NUM_MAX){
//            username = username.substring(0, UserProfile.USERNAME_NUM_MAX);
//        }
//        UserProfile userProfileStorage = new UserProfile();
//        userProfileStorage.setUsername(username);
//        userProfileStorage.setAvatar(avatar);
//        userProfileStorage.setEmail(email);
//        userProfileStorage.setIntroduction(signature);
//        userProfileStorage.setLocation(region);
//
//
//
//        UserProfile temp = new UserProfile();
//        temp.setUsername(username);
//        UserProfile temp1 = profileMapper.selectByUserInfo(temp);
//        Random random = new Random();
//        int n = UserProfile.USERNAME_NUM_MAX - username.length();
//        if(temp1 != null){
//            // 该第三方中的用户名已在本地登陆存在,随机生成用户名后缀
//            if(n == 0){
//                String suffix = random.nextInt(1000000) + "";
//                userProfileStorage.setUsername("用户" + suffix);
//            } else{
//                String suffix = random.nextInt((int) Math.pow(10, n)) + "";
//                userProfileStorage.setUsername(username + suffix);
//            }
//        }
//
//        long userId = IdUtil.getSnowflake(23, 17).nextId();
//        userProfileStorage.setUserId(userId);
//        transactionTemplate.execute(status -> {
//            accountMapper.saveUser(userId);
//
//            // 新增用户信息表
//            try {
//                profileMapper.saveUserProfile(userProfileStorage);
//            } catch (Exception e) {
//                String suffix = random.nextInt(1000000) + "";
//                userProfileStorage.setUsername("用户" + suffix);
//                profileMapper.saveUserProfile(userProfileStorage);
//            }
//
//            String accessToken = (String) attributes.get("access_token");
//            UserAuth userAuth = new UserAuth();
//            userAuth.setUserId(userId);
//            userAuth.setIdentityType("github");
//            userAuth.setIdentifier(String.valueOf(authId));
//            userAuth.setCredential(accessToken);
//
//            // 新增用户认证表
//            authMapper.saveUserAuth(userAuth);
//
//            return null;
//        });
//
//
//        // 新增该用户普通用户权限
//        menuMapper.setUserRole(userId, Role.USER_ROLE);
//        menuMapper.setUserRole(userId, "github");
//
//        List<String> permissions = menuMapper.selectPermsByUserId(userProfileStorage.getUserId());
//        List<String> roles = menuMapper.selectRoleByUserId(userProfileStorage.getUserId());
//        BasicsUser basicsUser = new BasicsUser();
//        basicsUser.setUserId(userProfileStorage.getUserId());
//        basicsUser.setStatus(accountMapper.selectById(userProfileStorage.getUserId()).getStatus());
//
//
//        if (email != null) {
//            UserProfile temp2 = new UserProfile();
//            temp2.setEmail(email);
//            UserProfile user4 = profileMapper.selectByUserInfo(temp2);
//            if(user4 != null){
//                // 该第三方中的邮箱已在本地登陆存在，告知用户是否合并账号
//                return new LoginUser(basicsUser, permissions, roles, attributes, true);
//            }
//        }
//
//        return new LoginUser(basicsUser, permissions, roles, attributes);
//    }
}

