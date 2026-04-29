package org.lixiyun.pojo.vo.admin.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员信息VO
 *
 * @author lixiyun
 * @since 2026-04-20
 */
@Schema(description = "管理员信息VO")
@Data
public class AdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "管理员ID")
    private Long id;

    @Schema(description = "管理员用户名")
    private String username;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号码")
    private String mobile;

    @Schema(description = "账号状态：0=正常，1=异常，2=封禁，3=注销")
    private Integer status;

    @Schema(description = "封禁开始时间")
    private LocalDateTime banTime;

    @Schema(description = "封禁结束时间，表示到这时进行解封，如果是封禁状态，但这里是null，则是永久封禁")
    private Integer banEndTime;

    @Schema(description = "封禁理由")
    private String banReason;

    @Schema(description = "注册时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}
