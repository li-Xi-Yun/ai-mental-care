package org.lixiyun.pojo.dto.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

@Data
@Schema(description = "诊断书用户反馈 DTO")
public class EmotionDiagnosisFeedbackDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NumberOfRanges(min = 1, max = 5, message = "诊断打分要在1~5")
    @Schema(description = "用户对本次诊断打分 1~5分，NULL 代表未评分", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer diagnosisScore;

    @Size(max = 200, message = "用户文字反馈长度不能超过200个字符")
    @Schema(description = "用户文字反馈、吐槽、补充意见", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String feedbackContent;

    @NumberOfRanges(max = 1, message = "是否认同风险评估取值范围为0-1")
    @Schema(description = "是否认同风险评估：0-不认同 1-认同 NULL 未反馈", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer agreeRiskJudge;

    @NumberOfRanges(max = 1, message = "是否认同自助调节建议取值范围为0-1")
    @Schema(description = "是否认同给出的自助调节建议：0-不认同 1-认同 NULL 未反馈", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer agreeSuggestionSelf;

    @NumberOfRanges(max = 1, message = "是否认同社会支持建议取值范围为0-1")
    @Schema(description = "是否认同给出的社会支持建议：0-不认同 1-认同 NULL 未反馈", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer agreeSuggestionSocial;

    @NumberOfRanges(max = 1, message = "是否认同专业干预建议取值范围为0-1")
    @Schema(description = "是否认同给出的专业干预建议：0-不认同 1-认同 NULL 未反馈", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer agreeSuggestionProfessional;

    @NumberOfRanges(max = 2, message = "是否尝试采纳建议取值范围为0-2")
    @Schema(description = "是否尝试采纳建议：0-没有 1-尝试部分 2-全部尝试 NULL 未反馈", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer useSuggestion;

}