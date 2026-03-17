package org.lixiyun.server.login;

import org.lixiyun.pojo.tool.LoginUser;

import java.util.Map;

public interface OAuth2UserConverter {


    /**
     * 将第三方平台的用户信息attributes转换成本地的用户信息LoginUser
     * @param attributes 第三方平台用户信息
     * @return 本地用户信息LoginUser
     */
    LoginUser convert(Map<String, Object> attributes);
}
