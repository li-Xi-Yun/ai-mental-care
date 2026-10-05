package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 诊断反馈展示 VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "诊断反馈展示 VO")
public class AssessmentFeedbackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "反馈记录主键 ID")
    private Long id;

    @Schema(description = "诊断书 ID")
    private Long diagnosisId;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "用户对本次诊断打分 1~5分，NULL 代表未评分")
    private Integer diagnosisScore;

    @Schema(description = "用户文字反馈、吐槽、补充意见")
    private String feedbackContent;

    @Schema(description = "是否认同风险评估：0-不认同 1-认同 NULL未反馈")
    private Integer agreeRiskJudge;

    @Schema(description = "是否认同给出的自助调节建议：0-不认同 1-认同 NULL未反馈")
    private Integer agreeSuggestionSelf;

    @Schema(description = "是否认同给出的社会支持建议：0-不认同 1-认同 NULL未反馈")
    private Integer agreeSuggestionSocial;

    @Schema(description = "是否认同给出的专业干预建议：0-不认同 1-认同 NULL未反馈")
    private Integer agreeSuggestionProfessional;

    @Schema(description = "是否尝试采纳建议：0-没有 1-尝试部分 2-全部尝试 NULL未反馈")
    private Integer useSuggestion;

    @Schema(description = "用户提交反馈时间")
    private LocalDateTime feedbackTime;

    @Schema(description = "记录创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updatedTime;

}
