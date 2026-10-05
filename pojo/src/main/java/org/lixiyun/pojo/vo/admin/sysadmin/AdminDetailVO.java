package org.lixiyun.pojo.vo.admin.sysadmin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员详情VO
 *
 * @author lixiyun
 * @since 2026-07-30
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "管理员详情VO")
public class AdminDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "管理员ID")
    private Long id;

    @Schema(description = "管理员账号")
    private String loginAccount;

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

    @Schema(description = "封禁结束时间")
    private LocalDateTime banEndTime;

    @Schema(description = "封禁理由")
    private String banReason;

    @Schema(description = "账号更新时间")
    private LocalDateTime loginAccountUpdateTime;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "创建人姓名")
    private String createdByName;

    @Schema(description = "注册时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新人ID")
    private Long updatedBy;

    @Schema(description = "更新人姓名")
    private String updatedByName;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "是否删除：0=未删除，1=已删除")
    private Integer deleted;
}