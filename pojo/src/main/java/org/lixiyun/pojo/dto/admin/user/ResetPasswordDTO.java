package org.lixiyun.pojo.dto.admin.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 重置密码DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Schema(description = "重置密码DTO")
@Data
public class ResetPasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "新密码不能为空")
    @Schema(description = "新密码，必须包含大写字母、小写字母和数字，且长度为6~20位", example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}