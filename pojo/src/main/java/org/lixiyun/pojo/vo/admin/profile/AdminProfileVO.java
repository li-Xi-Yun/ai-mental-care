package org.lixiyun.pojo.vo.admin.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author lixiyun
 * @since 2026-04-18 21:34
 */
@Data
@Schema(description = "管理员个人资料")
public class AdminProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "管理员ID")
    private Long id;

    @Schema(description = "账号名")
    private String loginAccount;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "手机号码")
    private String mobile;

    @Schema(description = "邮箱地址")
    private String email;

    @Schema(description = "账号状态：0=正常，1=异常，2=封禁，3=注销")
    private Integer status;

    @Schema(description = "封禁理由")
    private String banReason;

    @Schema(description = "封禁开始时间")
    private LocalDateTime banTime;

    @Schema(description = "封禁结束时间")
    private LocalDateTime banEndTime;

    @Schema(description = "注册时间")
    private LocalDateTime createdTime;

}
