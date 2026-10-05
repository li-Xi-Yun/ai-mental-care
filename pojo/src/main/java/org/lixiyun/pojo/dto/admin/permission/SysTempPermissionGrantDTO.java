package org.lixiyun.pojo.dto.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 授予临时权限DTO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Schema(description = "授予临时权限DTO")
public class SysTempPermissionGrantDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long personId;

    @NotEmpty(message = "权限ID列表不能为空")
    @Schema(description = "权限ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> permissionIdList;

    @NotNull(message = "权限生效时间不能为空")
    @Schema(description = "权限生效时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @NotNull(message = "权限过期时间不能为空")
    @Schema(description = "权限过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime expireTime;

    @Length(max = 200, message = "授予原因长度不能超过200个字符")
    @Schema(description = "授予该临时权限的原因", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String grantReason;
}