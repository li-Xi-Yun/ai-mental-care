package org.lixiyun.pojo.dto.admin.sysadmin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.lixiyun.common.validation.annotation.NotReservedWord;

import java.io.Serializable;

/**
 * 新增管理员DTO
 *
 * @author lixiyun
 * @since 2026-07-30
 */
@Data
@Schema(description = "新增管理员DTO")
public class AdminAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @NotReservedWord(message = "用户名不能为保留字")
    @Length(min = 2, max = 12, message = "用户名长度为2-12个字符")
    @Schema(description = "用户名，长度为2-12个字符，不能为保留字", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,20}$", message = "密码必须包含大写字母、小写字母和数字，且长度为6~20位")
    @Schema(description = "密码，必须包含大写字母、小写字母和数字，且长度为6~20位", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String password;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    @Schema(description = "手机号码", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String mobile;

    @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;

    @Schema(description = "是否由系统自动生成密码", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean sysPassword;
}