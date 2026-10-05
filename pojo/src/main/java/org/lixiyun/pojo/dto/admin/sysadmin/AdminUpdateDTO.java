package org.lixiyun.pojo.dto.admin.sysadmin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NotReservedWord;

import java.io.Serializable;

/**
 * 修改管理员基础信息DTO
 *
 * @author lixiyun
 * @since 2026-07-30
 */
@Data
@Schema(description = "修改管理员基础信息DTO")
public class AdminUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "管理员ID不能为空")
    @Schema(description = "管理员ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotReservedWord(message = "用户名不能为保留字")
    @Size(min = 2, max = 12, message = "用户名长度为2~12个字符")
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String username;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    @Schema(description = "手机号码", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String mobile;

    @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;
}