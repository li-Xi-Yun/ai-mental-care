package org.lixiyun.pojo.dto.user.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NotReservedWord;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

@Data
@Schema(description = "用户资料信息")
public class UserProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotReservedWord(message = "用户名不能为null，NAN，default，空")
    @Schema(description = "用户名,用户名长度为2~12、不能为null，NAN，default，空", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 50, message = "用户名长度为2~12")
    private String username;

    @Schema(description = "用户头像URL，这是是由图片上传接口的返回值获取的", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "头像URL长度不能超过255个字符")
    private String avatar;

    @Schema(description = "手机号码", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String phone;

    @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Email(message = "邮箱格式不正确")
    private String email;

    // 用户简介
    @Schema(description = "用户简介，长度不能超过80个字符", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 80, message = "用户简介长度不能超过80个字符")
    private String introduction;

    // 性别，0：女，1：男,2：未知
    @Schema(description = "性别，0：女，1：男,2：未知", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @NumberOfRanges(max = 2, message = "性别只能是0：女，1：男,2：未知")
    private Integer gender;

    // 所在地
    @Schema(description = "所在地", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 30, message = "所在地长度不能超过30个字符")
    private String location;

}
