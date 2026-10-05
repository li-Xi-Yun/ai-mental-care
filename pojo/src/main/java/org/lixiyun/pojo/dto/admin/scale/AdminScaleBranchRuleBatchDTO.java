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
 * 跳题规则批量保存DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "跳题规则批量保存DTO")
public class AdminScaleBranchRuleBatchDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "量表版本ID不能为空")
    @Schema(description = "量表版本ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long scaleVersionId;

    @NotEmpty(message = "规则列表不能为空")
    @Valid
    @Schema(description = "跳题规则列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AdminScaleBranchRuleDTO> rules;
}