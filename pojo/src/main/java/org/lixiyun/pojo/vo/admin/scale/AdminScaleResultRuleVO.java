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
 * 结果规则VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "结果规则VO")
public class AdminScaleResultRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "规则ID", example = "1")
    private Long id;

    @Schema(description = "量表版本ID", example = "1")
    private Long scaleVersionId;

    @Schema(description = "维度ID，NULL代表总分规则", example = "1")
    private Long dimensionId;

    @Schema(description = "维度名称", example = "抑郁")
    private String dimensionName;

    @Schema(description = "区间最低分（包含）", example = "53")
    private BigDecimal minScore;

    @Schema(description = "区间最高分（包含）", example = "62")
    private BigDecimal maxScore;

    @Schema(description = "测评结果描述文本", example = "轻度抑郁状态")
    private String resultText;

    @Schema(description = "风险等级：0=无 1=低 2=中 3=高（预警）", example = "1")
    private Integer riskLevel;

    @Schema(description = "排序序号", example = "0")
    private Integer sort;

    @Schema(description = "创建时间", example = "2026-10-05 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间", example = "2026-10-05 10:00:00")
    private LocalDateTime updatedTime;
}