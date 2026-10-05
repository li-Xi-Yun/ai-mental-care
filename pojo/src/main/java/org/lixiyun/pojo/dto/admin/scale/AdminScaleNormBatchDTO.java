package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 常模明细细分页/批量保存DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "常模明细批量保存DTO")
public class AdminScaleNormBatchDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "常模组ID不能为空")
    @Schema(description = "常模组ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long normGroupId;

    @NotEmpty(message = "常模明细列表不能为空")
    @Valid
    @Schema(description = "常模明细列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AdminScaleNormDTO> items;
}