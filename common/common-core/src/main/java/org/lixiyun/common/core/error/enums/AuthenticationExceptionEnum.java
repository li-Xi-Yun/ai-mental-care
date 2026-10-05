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
    USER_STATUS_ABNORMAL("用户状态异常", 1019),


    // 以下是网页浏览的错误信息
    JWT_ERROR("JWT错误", 1012),
    USER_NOT_PRIVILEGE("权限不足", 1013),
    USER_NOT_LOGIN("用户未登录", 1014),

    USER_STATUS_UPDATE_FAILED("用户状态修改失败", 1016),
    ADMIN_NOT_FOUND("管理员不存在", 1017),
    ADMIN_STATUS_UPDATE_FAILED("管理员状态修改失败", 1018),

    // 账号名相关
    LOGIN_ACCOUNT_EXIST("账号名已存在", 1020),
    LOGIN_ACCOUNT_UPDATE_TOO_FREQUENT("账号名修改过于频繁，180天内仅能修改一次", 1021),

    // 用户/管理员创建与更新
    USER_CREATE_FAILED("用户创建失败", 1022),
    USER_UPDATE_FAILED("用户更新失败", 1023),
    ADMIN_CREATE_FAILED("管理员创建失败", 1024),
    ADMIN_UPDATE_FAILED("管理员更新失败", 1025),
    USER_DELETE_FAILED("用户注销失败", 1026),
    ADMIN_DELETE_FAILED("管理员注销失败", 1027),
    PASSWORD_RESET_FAILED("密码重置失败", 1028),

    // 角色权限
    SUPER_ADMIN_ONLY("仅超级管理员可执行该操作", 1029),
    ROLE_UPDATE_FAILED("角色更新失败", 1030),



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
