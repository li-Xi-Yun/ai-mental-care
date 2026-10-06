package org.lixiyun.pojo.vo.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 临时权限记录VO
 *
 * @author lixiyun
 * @since 2026-07-30
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "临时权限记录VO")
public class TempPermissionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "临时权限记录ID")
    private Long id;

    @Schema(description = "人员ID")
    private Long personId;

    @Schema(description = "人员名称")
    private String personUsername;

    @Schema(description = "权限ID")
    private Long permissionId;

    @Schema(description = "权限标识符")
    private String perms;

    @Schema(description = "权限名称")
    private String permissionName;

    @Schema(description = "权限分组名称")
    private String groupName;

    @Schema(description = "权限生效时间")
    private LocalDateTime startTime;

    @Schema(description = "权限过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "状态：0=有效，1=手动作废，2=已过期")
    private Integer status;

    @Schema(description = "授予该临时权限的原因")
    private String grantReason;

    @Schema(description = "操作授予人的用户ID")
    private Long grantUserId;

    @Schema(description = "授予人名称")
    private String grantUsername;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
