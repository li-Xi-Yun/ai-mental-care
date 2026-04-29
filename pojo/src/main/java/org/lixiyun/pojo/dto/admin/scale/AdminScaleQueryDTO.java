package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * 管理员量表查询DTO
 * <p>支持删除状态、启用状态、名称模糊匹配等查询条件</p>
 *
 * @author lixiyun
 * @since 2026-04-19
 */
@Schema(description = "管理员量表查询DTO")
@Data
public class AdminScaleQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "页码不能为空")
    @NumberOfRanges
    private Integer pageNum;

    @Schema(description = "每页数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "每页数量不能为空")
    @NumberOfRanges
    private Integer pageSize;

    @Schema(description = "量表名称（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String scaleName;

    @Schema(description = "启用状态：1=启用，0=禁用", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;

    @Schema(description = "删除状态：0=未删除，1=已删除，", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer isDeleted;
}
