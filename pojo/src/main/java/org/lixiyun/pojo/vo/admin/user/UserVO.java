package org.lixiyun.pojo.vo.admin.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息VO
 *
 * @author lixiyun
 * @since 2026-04-20
 */
@Schema(description = "用户信息VO")
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "邮箱号")
    private String email;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "性别，0=女，1=男，2=未知")
    private Integer gender;

    @Schema(description = "用户头像路径")
    private String avatar;

    @Schema(description = "账号状态：0=正常，1=异常，2=封禁，3=注销")
    private Integer status;

    @Schema(description = "封禁理由")
    private String banReason;

    @Schema(description = "封禁开始时间")
    private LocalDateTime banTime;

    @Schema(description = "封禁结束时间，表示到这时进行解封，如果是封禁状态，但这里是null，则是永久封禁")
    private LocalDateTime banEndTime;

    @Schema(description = "注册时间")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updatedTime;
}
