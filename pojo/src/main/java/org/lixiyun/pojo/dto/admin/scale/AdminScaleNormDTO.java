package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 常模明细新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "常模明细新增/修改DTO")
public class AdminScaleNormDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "明细ID（更新时必填）", example = "1")
    private Long id;

    @NotNull(message = "常模组ID不能为空", groups = {AddGroup.class})
    @Schema(description = "常模组ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long normGroupId;

    @Schema(description = "维度ID（冗余，方便直接查询）NULL=总分", example = "1")
    private Long dimensionId;

    @NotNull(message = "原始分不能为空", groups = {AddGroup.class})
    @Schema(description = "原始分", requiredMode = Schema.RequiredMode.REQUIRED, example = "20.00")
    private BigDecimal rawScore;

    @Schema(description = "T分：均值50 标准差10", example = "45.00")
    private BigDecimal tScore;

    @Schema(description = "Z分：均值0 标准差1", example = "-0.50")
    private BigDecimal zScore;

    @Schema(description = "百分等级 0.00~100.00", example = "30.00")
    private BigDecimal percentile;

    @Schema(description = "标准九 1~9", example = "4")
    private Integer stanine;

    @Schema(description = "离差智商：均值100 标准差15", example = "95.00")
    private BigDecimal diq;

    @Schema(description = "等级标签：0=极低 1=偏低 2=正常 3=偏高 4=极高", example = "2")
    private Integer levelLabel;
}