package org.lixiyun.pojo.vo.user.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "用户个人资料展示")
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "账号名")
    private String loginAccount;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户头像URL")
    private String avatar;

    @Schema(description = "手机号码")
    private String mobile;

    @Schema(description = "邮箱地址")
    private String email;

    @Schema(description = "用户简介")
    private String introduction;

    @Schema(description = "性别，0：女，1：男,2：未知")
    private Integer gender;

    @Schema(description = "账号状态：0=正常，1=异常，2=封禁，3=注销")
    private Integer status;

    @Schema(description = "封禁理由")
    private String banReason;

    @Schema(description = "封禁开始时间")
    private LocalDateTime banTime;

    @Schema(description = "封禁结束时间，封禁状态且为null表示永久封禁")
    private LocalDateTime banEndTime;

    @Schema(description = "账号名修改时间")
    private LocalDateTime loginAccountUpdateTime;

    @Schema(description = "注册时间")
    private LocalDateTime createdTime;

}