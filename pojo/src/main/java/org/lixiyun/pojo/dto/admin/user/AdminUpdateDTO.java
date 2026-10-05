package org.lixiyun.pojo.dto.admin.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NotReservedWord;

import java.io.Serializable;

/**
 * 管理端编辑管理员DTO（含角色调整）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Schema(description = "编辑管理员DTO")
@Data
public class AdminUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotReservedWord(message = "用户名不能为null，NAN，default，空")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "用户名长度为2~12")
    private String username;

    @Schema(description = "管理员账号名，可选（仅超级管理员可修改）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 32, message = "账号名长度不能超过32个字符")
    private String loginAccount;

    @Schema(description = "手机号码", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String mobile;

    @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "角色：ADMIN=普通管理员，SUPER_ADMIN=超级管理员，可选", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String role;
}