package org.lixiyun.pojo.dto.admin.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NotReservedWord;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-04-18 20:50
 */
@Data
@Schema(description = "管理员登录数据传输对象")
public class LoginAdminDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @NotReservedWord
    @Schema(description = "管理员账号名，不能为保留字", example = "super_admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String loginAccount;

    @NotBlank
    @Schema(description = "密码", example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    // 验证码对应在redis中的key
    @Schema(description = "普通验证码的答案key，本地登录使用", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String key;

    // 验证码
    @NotBlank
    @Schema(description = "普通验证码", example = "ABCD")
    private String code;

}