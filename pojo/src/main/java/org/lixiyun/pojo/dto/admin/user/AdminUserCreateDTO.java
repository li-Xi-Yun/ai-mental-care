package org.lixiyun.pojo.dto.admin.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.lixiyun.common.validation.annotation.NotReservedWord;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * 管理端新增用户DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Schema(description = "管理端新增用户DTO")
@Data
public class AdminUserCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @NotReservedWord(message = "账号名不能为null，NAN，default，空")
    @Length(min = 2, max = 32, message = "账号名长度为2~32个字符")
    @Schema(description = "用户账号名，不能重复", requiredMode = Schema.RequiredMode.REQUIRED)
    private String loginAccount;

    @NotBlank
    @NotReservedWord(message = "用户名不能为null，NAN，default，空")
    @Length(min = 2, max = 12, message = "用户名长度为2~12个字符")
    @Schema(description = "用户名（显示名）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank
    @Schema(description = "密码，必须包含大写字母、小写字母和数字，且长度为6~20位", example = "Password123!")
    private String password;

    @Schema(description = "手机号，可选", example = "13800000000", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String mobile;

    @Schema(description = "邮箱，可选", example = "zhangsan@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过50个字符")
    private String email;

    @Schema(description = "性别，0：女，1：男，2：未知", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @NumberOfRanges(max = 2, message = "性别只能是0：女，1：男,2：未知")
    private Integer gender;
}