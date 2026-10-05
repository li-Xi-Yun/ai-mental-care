package org.lixiyun.pojo.dto.admin.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NotReservedWord;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * 管理端编辑用户DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Schema(description = "管理端编辑用户DTO")
@Data
public class AdminUserUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotReservedWord(message = "用户名不能为null，NAN，default，空")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "用户名长度为2~12")
    private String username;

    @Schema(description = "用户账号名，可选", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 32, message = "账号名长度不能超过32个字符")
    private String loginAccount;

    @Schema(description = "手机号码", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String mobile;

    @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "用户头像URL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "头像URL长度不能超过255个字符")
    private String avatar;

    @Schema(description = "性别，0：女，1：男，2：未知", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @NumberOfRanges(max = 2, message = "性别只能是0：女，1：男,2：未知")
    private Integer gender;
}