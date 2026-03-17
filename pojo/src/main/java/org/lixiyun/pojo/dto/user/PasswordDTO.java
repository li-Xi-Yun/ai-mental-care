package org.lixiyun.pojo.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.lixiyun.common.validation.group.UserGroup;

import java.io.Serializable;

@Data
public class PasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(groups = UserGroup.PwdUpdate.class)
    @Schema(description = "原密码,用于修改密码", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String originalPassword;

    @NotBlank
    @Schema(description = "新密码，必须包含大写字母、小写字母和数字，且长度为6~20位", example = "Password123!")
    private String password;

    @Email(groups = UserGroup.ForgetPwd.class)
    @NotBlank(groups = UserGroup.ForgetPwd.class)
    @Schema(description = "邮箱地址，用于忘记密码", example = "example@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;

    // 验证码
    @NotBlank(groups = UserGroup.ForgetPwd.class)
    @Schema(description = "验证码，用于忘记密码", example = "ABCD", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String code;

}