package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户测评维度得分VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户测评维度得分VO")
public class AdminScaleDimScoreVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "维度ID", example = "1")
    private Long dimensionId;

    @Schema(description = "维度名称", example = "抑郁")
    private String dimensionName;

    @Schema(description = "维度得分快照", example = "12.00")
    private BigDecimal dimScore;

    @Schema(description = "维度解读快照", example = "轻度抑郁")
    private String dimResult;

    @Schema(description = "维度风险等级：0=无 1=低 2=中 3=高", example = "1")
    private Integer riskLevel;
}