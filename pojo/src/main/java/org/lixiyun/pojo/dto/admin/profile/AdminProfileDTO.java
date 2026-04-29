package org.lixiyun.pojo.dto.admin.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NotReservedWord;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-04-18 21:29
 */
@Data
@Schema(description = "管理员个人资料DTO")
public class AdminProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotReservedWord(message = "用户名不能为null，NAN，default，空")
    @Schema(description = "用户名,用户名长度为2~12、不能为null，NAN，default，空", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "用户名长度为2~12")
    private String username;

    @Schema(description = "手机号码", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String phone;

    @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "邮箱格式不正确")
    private String email;

}
