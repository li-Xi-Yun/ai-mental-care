package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * 量表状态修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表状态修改DTO")
public class AdminScaleStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "状态不能为空")
    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "状态：0=禁用 1=启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;
}