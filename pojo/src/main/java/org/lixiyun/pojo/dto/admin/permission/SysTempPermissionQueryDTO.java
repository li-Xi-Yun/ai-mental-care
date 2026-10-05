package org.lixiyun.pojo.dto.admin.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;

/**
 * 临时权限查询DTO
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Data
@Schema(description = "临时权限查询DTO")
public class SysTempPermissionQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long personId;

    @NumberOfRanges(min = 0, max = 2)
    @Schema(description = "状态：0=有效，1=手动作废，2=已过期", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;
}