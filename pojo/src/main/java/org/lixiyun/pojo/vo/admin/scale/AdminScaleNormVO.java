package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 常模明细VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "常模明细VO")
public class AdminScaleNormVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "明细ID", example = "1")
    private Long id;

    @Schema(description = "常模组ID", example = "1")
    private Long normGroupId;

    @Schema(description = "维度ID（冗余），NULL=总分", example = "1")
    private Long dimensionId;

    @Schema(description = "原始分", example = "20.00")
    private BigDecimal rawScore;

    @Schema(description = "T分", example = "45.00")
    private BigDecimal tScore;

    @Schema(description = "Z分", example = "-0.50")
    private BigDecimal zScore;

    @Schema(description = "百分等级", example = "30.00")
    private BigDecimal percentile;

    @Schema(description = "标准九", example = "4")
    private Integer stanine;

    @Schema(description = "离差智商", example = "95.00")
    private BigDecimal diq;

    @Schema(description = "等级标签：0=极低 1=偏低 2=正常 3=偏高 4=极高", example = "2")
    private Integer levelLabel;

    @Schema(description = "创建时间", example = "2026-10-05 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间", example = "2026-10-05 10:00:00")
    private LocalDateTime updatedTime;
}