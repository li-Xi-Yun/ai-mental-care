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
 * 用户测评记录VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户测评记录VO")
public class AdminScaleRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "测评记录ID", example = "1")
    private Long recordId;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户姓名", example = "张三")
    private String userName;

    @Schema(description = "量表ID", example = "1")
    private Long scaleId;

    @Schema(description = "量表名称", example = "抑郁自评量表")
    private String scaleName;

    @Schema(description = "量表版本ID", example = "1")
    private Long scaleVersionId;

    @Schema(description = "量表版本号", example = "v1.0")
    private String versionNo;

    @Schema(description = "本次测评使用的常模组ID", example = "1")
    private Long normGroupId;

    @Schema(description = "最终计算原始总分", example = "35.00")
    private BigDecimal totalScore;

    @Schema(description = "标准分（如T分）", example = "50.00")
    private BigDecimal standardScore;

    @Schema(description = "百分等级快照", example = "50.00")
    private BigDecimal percentile;

    @Schema(description = "风险等级：0=无 1=低 2=中 3=高（预警）", example = "2")
    private Integer riskLevel;

    @Schema(description = "本次测评结果描述", example = "中度抑郁状态")
    private String resultText;

    @Schema(description = "作答状态：0=未完成 1=已完成 2=中途终止", example = "1")
    private Integer finishStatus;

    @Schema(description = "开始作答时间", example = "2026-10-05 10:00:00")
    private LocalDateTime startTime;

    @Schema(description = "提交时间", example = "2026-10-05 10:20:00")
    private LocalDateTime endTime;

    @Schema(description = "记录创建时间", example = "2026-10-05 10:20:00")
    private LocalDateTime createdTime;
}