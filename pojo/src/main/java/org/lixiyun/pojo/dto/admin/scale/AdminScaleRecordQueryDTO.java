package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户测评记录分页查询DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户测评记录分页查询DTO")
public class AdminScaleRecordQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "量表ID", example = "1")
    private Long scaleId;

    @Schema(description = "量表名称（支持模糊匹配）", example = "抑郁")
    private String scaleName;

    @NumberOfRanges(min = 0, max = 3)
    @Schema(description = "风险等级：0=无 1=低 2=中 3=高", example = "3")
    private Integer riskLevel;

    @NumberOfRanges(min = 0, max = 2)
    @Schema(description = "作答状态：0=未完成 1=已完成 2=中途终止", example = "1")
    private Integer finishStatus;

    @Schema(description = "开始时间（查询范围起）", example = "2026-10-01 00:00:00")
    private LocalDateTime startTime;

    @Schema(description = "结束时间（查询范围止）", example = "2026-10-05 23:59:59")
    private LocalDateTime endTime;
}