package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的测评历史记录 VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "我的测评历史记录 VO")
public class ScaleRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "测评记录ID", example = "10001")
    private Long recordId;

    @Schema(description = "量表ID", example = "1")
    private Long scaleId;

    @Schema(description = "量表名称（记录快照）")
    private String scaleName;

    @Schema(description = "原始总分")
    private BigDecimal totalScore;

    @Schema(description = "标准分", example = "55.00")
    private BigDecimal standardScore;

    @Schema(description = "百分等级")
    private BigDecimal percentile;

    @Schema(description = "风险等级：0-无 1-低 2-中 3-高", example = "0")
    private Integer riskLevel;

    @Schema(description = "结果描述文本")
    private String resultText;

    @Schema(description = "完成状态：0-未完成 1-已完成 2-中途终止", example = "1")
    private Integer finishStatus;

    @Schema(description = "开始作答时间")
    private LocalDateTime startTime;

    @Schema(description = "提交/结束时间")
    private LocalDateTime endTime;

    @Schema(description = "记录创建时间")
    private LocalDateTime createdTime;

}