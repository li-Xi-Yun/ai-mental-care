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
    ROLE_UPDATE_FAILED("角色修改失败", 1030),

    PERMISSION_NOT_FOUND("权限不存在", 1031),
    PERMISSION_EXIST("权限标识符或名称或分组名称已存在", 1032),
    PERMISSION_ADD_FAILED("权限新增失败", 1033),
    PERMISSION_UPDATE_FAILED("权限修改失败", 1034),
    PERMISSION_DELETE_FAILED("权限删除失败", 1035),
    TEMP_PERMISSION_NOT_FOUND("临时权限记录不存在", 1036),
    TEMP_PERMISSION_ALREADY_REVOKED("临时权限已作废", 1037),
    TEMP_PERMISSION_GRANT_FAILED("临时权限授予失败", 1038),
    TEMP_PERMISSION_REVOKE_FAILED("临时权限作废失败", 1039),

    ROLE_NOT_FOUND("角色不存在", 1040),
    ROLE_EXIST("角色标识或名称已存在", 1041),
    ROLE_ADD_FAILED("角色新增失败", 1042),
    ROLE_DELETE_FAILED("角色删除失败", 1043),
    ROLE_STATUS_UPDATE_FAILED("角色状态修改失败", 1044),
    ROLE_PERMISSION_QUERY_FAILED("角色权限查询失败", 1045),
    ROLE_PERMISSION_ASSIGN_FAILED("角色权限分配失败", 1046),

    ADMIN_ADD_FAILED("管理员新增失败", 1047),
    ADMIN_PASSWORD_RESET_FAILED("管理员密码重置失败", 1048),
    ADMIN_ROLE_ASSIGN_FAILED("管理员角色分配失败", 1049),
    ADMIN_USERNAME_EXIST("管理员用户名已存在", 1050),
    ADMIN_EMAIL_EXIST("管理员邮箱已存在", 1051),
    ADMIN_MOBILE_EXIST("管理员手机号已存在", 1052),
    DEFAULT_PASSWORD_NOT_CONFIGURED("默认密码未配置", 1053),
    CAN_NOT_BAN_SELF("不能禁用自己", 1054),
    BAN_END_TIME_CANNOT_BE_BEFORE_BAN_START_TIME("封禁结束时间不能早于当前或封禁开始时间", 1055),

    USER_NOT_FOUND("用户不存在", 1056),
    USER_ROLE_ASSIGN_FAILED("用户角色分配失败", 1057),
    USER_BAN_END_TIME_INVALID("封禁结束时间无效", 1058),
    USER_CANT_ASSIGN_ROLE_TO_SELF("用户不能给自己分配角色", 1059),

    MULTIPLE_TOKEN_ERROR("请求中同时存在多个token，将优先使用第一个获取到的token。建议前端检查是否重复传递token", 1060),



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
