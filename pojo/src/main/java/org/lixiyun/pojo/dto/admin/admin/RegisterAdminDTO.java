package org.lixiyun.pojo.dto.admin.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.lixiyun.common.validation.annotation.NotReservedWord;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-04-18 20:45
 */
@Data
@Schema(description = "注册管理员DTO")
public class RegisterAdminDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @NotReservedWord
    @Length(min=2, max=12)
    @Schema(description = "用户名，长度为2-12个字符，不能为保留字", example = "张三")
    private String username;

    @NotBlank
    @Schema(description = "密码，必须包含大写字母、小写字母和数字，且长度为6~20位", example = "Password123!")
    private String password;

    @NotBlank
    @Schema(description = "普通验证码对应存储在Redis中的key")
    private String key;

    // 验证码
    @NotBlank
    @Schema(description = "普通验证码", example = "ABCD")
    private String code;

}