package org.lixiyun.server.login.impl;

import org.lixiyun.common.authentication.oauth2.CustomOAuth2UserService;
import org.lixiyun.pojo.tool.LoginUser;
import org.lixiyun.server.login.OAuth2UserConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CustomOAuth2UserServiceImpl extends DefaultOAuth2UserService implements CustomOAuth2UserService {

    @Autowired
    private Map<String, OAuth2UserConverter> converters; // 注入所有策略实现,这里的key为类名的首字母小写

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User oauthUser = super.loadUser(request);
        String provider = request.getClientRegistration().getRegistrationId(); // 如 "github"

        // 选择策略
        OAuth2UserConverter converter = converters.get(provider + "UserConverter");

        String accessToken = request.getAccessToken().getTokenValue();
        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());

        attributes.put("access_token", accessToken);

        LoginUser convert = converter.convert(attributes);

        convert.setAccess_token(accessToken);

        return convert;
    }
}