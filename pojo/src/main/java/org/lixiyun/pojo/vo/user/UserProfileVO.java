package org.lixiyun.pojo.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户个人资料展示")
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户头像URL")
    private String avatar;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "邮箱地址")
    private String email;

    @Schema(description = "用户简介")
    private String introduction;

    @Schema(description = "性别，0：女，1：男,2：未知")
    private Integer gender;

    @Schema(description = "所在地")
    private String location;

}