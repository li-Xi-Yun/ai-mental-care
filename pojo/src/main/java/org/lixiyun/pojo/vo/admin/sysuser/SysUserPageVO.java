package org.lixiyun.pojo.vo.admin.sysuser;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户分页列表VO
 *
 * @author lixiyun
 * @since 2026-07-30
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "系统用户分页列表VO")
public class SysUserPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户账号")
    private String loginAccount;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "性别，0=女，1=男，2=未知")
    private Integer gender;

    @Schema(description = "用户头像路径")
    private String avatar;

    @Schema(description = "账号状态：0=正常，1=异常，2=封禁，3=注销")
    private Integer status;

    @Schema(description = "注册时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}