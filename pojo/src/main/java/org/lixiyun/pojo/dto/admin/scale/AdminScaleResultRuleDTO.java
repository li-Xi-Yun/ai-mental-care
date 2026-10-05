package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 结果规则新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "结果规则新增/修改DTO")
public class AdminScaleResultRuleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "规则ID（更新时必填）", example = "1")
    private Long id;

    @NotNull(message = "量表版本ID不能为空", groups = {AddGroup.class})
    @Schema(description = "量表版本ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long scaleVersionId;

    @Schema(description = "维度ID，NULL代表总分规则", example = "1")
    private Long dimensionId;

    @NotNull(message = "区间最低分不能为空", groups = {AddGroup.class})
    @Schema(description = "区间最低分（包含）", requiredMode = Schema.RequiredMode.REQUIRED, example = "53")
    private BigDecimal minScore;

    @NotNull(message = "区间最高分不能为空", groups = {AddGroup.class})
    @Schema(description = "区间最高分（包含）", requiredMode = Schema.RequiredMode.REQUIRED, example = "62")
    private BigDecimal maxScore;

    @NotBlank(message = "结果描述不能为空", groups = {AddGroup.class})
    @Schema(description = "测评结果描述文本", requiredMode = Schema.RequiredMode.REQUIRED, example = "轻度抑郁状态")
    private String resultText;

    @NotNull(message = "风险等级不能为空")
    @NumberOfRanges(min = 0, max = 3)
    @Schema(description = "风险等级：0=无 1=低 2=中 3=高（预警）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer riskLevel;

    @Schema(description = "排序序号", example = "0")
    private Integer sort;
}