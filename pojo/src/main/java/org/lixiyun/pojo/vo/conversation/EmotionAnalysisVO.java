package org.lixiyun.pojo.vo.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author lixiyun
 * @since 2026-03-19 23:14
 */
@Data
@Schema(description = "情绪分析展示")
public class EmotionAnalysisVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分析ID")
    private Long id;

    @Schema(description = "轮次")
    private Integer roundNum;

    @Schema(description = "情感分析详情")
    private String analysisContent;

    @Schema(description = "情绪标签")
    private String emotionLabel;

    @Schema(description = "情绪细分标签")
    private String emotionSubLabel;

    @Schema(description = "情绪置信度")
    private Double emotionScore;

    @Schema(description = "情绪趋势")
    private String emotionTrend;

    @Schema(description = "正向情绪占比")
    private Double positiveEmotionRatio;

    @Schema(description = "负向情绪占比")
    private Double negativeEmotionRatio;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
