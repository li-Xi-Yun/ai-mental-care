package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 提交测评结果 VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "提交测评结果 VO")
public class ScaleSubmitResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "测评记录ID", example = "10001")
    private Long recordId;

    @Schema(description = "完成状态：1-已完成", example = "1")
    private Integer finishStatus;

    @Schema(description = "原始总分", example = "26.00")
    private BigDecimal totalScore;

    @Schema(description = "标准分（如T分），由常模换算得出", example = "55.00")
    private BigDecimal standardScore;

    @Schema(description = "百分等级", example = "85.50")
    private BigDecimal percentile;

    @Schema(description = "风险等级：0-无 1-低 2-中 3-高（预警）", example = "0")
    private Integer riskLevel;

    @Schema(description = "结果描述文本")
    private String resultText;

    @Schema(description = "是否需要展示专业帮助引导（派生：riskLevel=3 时 true）", example = "false")
    private Boolean needFollowUp;

    @Schema(description = "维度得分与解读列表")
    private List<ScaleDimensionResultVO> dimensionResults;

}