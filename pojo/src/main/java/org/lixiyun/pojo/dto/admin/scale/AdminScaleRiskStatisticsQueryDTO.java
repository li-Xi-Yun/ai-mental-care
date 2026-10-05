package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 风险预警统计查询DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "风险预警统计查询DTO")
public class AdminScaleRiskStatisticsQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表ID，为空统计全部量表", example = "1")
    private Long scaleId;

    @Schema(description = "开始时间（统计范围起）", example = "2026-10-01 00:00:00")
    private LocalDateTime startTime;

    @Schema(description = "结束时间（统计范围止）", example = "2026-10-05 23:59:59")
    private LocalDateTime endTime;
}