package org.lixiyun.pojo.dto.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;
import java.util.List;

/**
 * 单题作答提交 DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "单题作答提交 DTO")
public class ScaleAnswerItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "题目ID不能为空")
    @Schema(description = "题目ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "201")
    private Long questionId;

    @NotNull(message = "作答状态不能为空")
    @NumberOfRanges(min = 0, max = 1, message = "作答状态只能是0-未作答或1-已作答")
    @Schema(description = "作答状态：0-未作答 1-已作答", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer answerStatus;

    @Schema(description = "本题耗时（秒），仅入参归档，不参与返回", example = "12")
    private Integer spendSeconds;

    @Schema(description = "选中的选项ID列表：单选传1个，多选传多个，填空传空列表")
    private List<Long> optionIds;

    @Schema(description = "填空/简答答案文本")
    private String answerText;

}
