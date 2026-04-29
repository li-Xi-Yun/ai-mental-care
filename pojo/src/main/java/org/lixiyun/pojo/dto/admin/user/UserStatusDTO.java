package org.lixiyun.pojo.dto.admin.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户状态修改DTO
 *
 * @author lixiyun
 * @since 2026-04-20
 */
@Schema(description = "用户状态修改DTO")
@Data
public class UserStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotNull(message = "账号状态不能为空")
    @NumberOfRanges(min = 0, max = 2)
    @Schema(description = "账号状态：0=正常，1=异常，2=封禁", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Schema(description = "封禁理由（状态为封禁时必填）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String banReason;

    @Schema(description = "封禁结束时间（状态为封禁且非永久封禁时必填）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDateTime banEndTime;
}
