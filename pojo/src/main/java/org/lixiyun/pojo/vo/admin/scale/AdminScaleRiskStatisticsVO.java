package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 风险预警统计VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "风险预警统计VO")
public class AdminScaleRiskStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表ID", example = "1")
    private Long scaleId;

    @Schema(description = "量表名称", example = "抑郁自评量表")
    private String scaleName;

    @Schema(description = "测评总人次", example = "1000")
    private Long totalCount;

    @Schema(description = "无风险人次", example = "600")
    private Long noRiskCount;

    @Schema(description = "低风险人次", example = "250")
    private Long lowRiskCount;

    @Schema(description = "中风险人次", example = "100")
    private Long mediumRiskCount;

    @Schema(description = "高风险人次", example = "50")
    private Long highRiskCount;

    @Schema(description = "高风险占比（派生，高风险人次/总人次）", example = "0.05")
    private BigDecimal highRiskRate;
}