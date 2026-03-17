package org.lixiyun.common.authentication.constant;

public interface LoginConstant {

    // 登录方式, 0-用户名登录, 1-邮箱登录
    Integer USERNAME_LOGIN = 0;
    Integer EMAIL_LOGIN = 1;

    // 用户状态
    Integer ACCOUNT_NORMAL = 0;
    Integer ACCOUNT_DISABLE = 1;
    Integer ACCOUNT_LOGOUT = 2;

    String FIRST_LOGIN_ONE_DAY_KEY = "login:first_one:";

}
