package org.lixiyun.common.core.error.enums;

import lombok.Getter;
import org.lixiyun.common.core.error.ErrorCode;

/**
 * @author lixiyun
 * @since 2025-12-13 13:14
 */
public enum AuthenticationExceptionEnum implements ErrorCode {

    // 以下是请求过程中的错误信息


    // 以下是普通用户操作时的错误信息
    USER_NOT_EXIST("用户不存在", 1003),
    USER_NICKNAME_EXIST("用户昵称已存在", 1004),
    USER_EMAIL_EXIST("邮箱已存在", 1005),
    CODE_ERROR("验证码错误", 1006),
    PASSWORD_AND_ACCOUNT_ERROR("密码或账号错误", 1007),
    USER_BANNED("用户被封禁", 1008),
    PASSWORD_ERROR("密码错误", 1009),
    USER_EXIST("用户已存在", 1010),
    ACCOUNT_STATUS_PENDING_REVIEW("账户待审核", 1011),


    // 以下是网页浏览的错误信息
    JWT_ERROR("JWT错误", 1012),
    USER_NOT_PRIVILEGE("权限不足", 1013),
    USER_NOT_LOGIN("用户未登录", 1014),


    CAN_NOT_MODIFIED_IN_AUDIT_OR_PUBLISH("不能修改审核中的文章或发布中的文章", 5000),


    ;

    @Getter
    private String msg;
    @Getter
    private int code;

    AuthenticationExceptionEnum(String msg, int code){
        this.msg = msg;
        this.code = code;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void setCode(int code) {
        this.code = code;
    }


}
