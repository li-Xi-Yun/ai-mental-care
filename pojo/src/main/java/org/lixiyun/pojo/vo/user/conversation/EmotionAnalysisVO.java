package org.lixiyun.pojo.vo.user.conversation;

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

    @Schema(description = "情感标签（如anger/开心/neutral/不满等）")
    private String emotionLabel;

    @Schema(description = "情感细分标签（如愤怒可细分“不满/暴怒/抱怨”）")
    private String emotionSubLabel;

    @Schema(description = "情感识别置信度（0-1，如0.9200）")
    private Double emotionConfidence;

    @Schema(description = "情绪本身的强烈程度（0-1，如0.9200）")
    private Double emotionIntensity;

    @Schema(description = "较上一轮的情绪变化趋势")
    private String emotionTrend;

    @Schema(description = "PAD愉悦度P，取值范围[-1,1]")
    private Double pScore;

    @Schema(description = "PAD唤醒度A，取值范围[-1,1]")
    private Double aScore;

    @Schema(description = "PAD支配度D，取值范围[-1,1]")
    private Double dScore;

    @Schema(description = "负向情绪占比（0-1）")
    private Double negativeEmotionRatio;

    @Schema(description = "中性情绪占比（0-1）")
    private Double neutralEmotionRatio;

    @Schema(description = "正向情绪占比（0-1）")
    private Double positiveEmotionRatio;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
