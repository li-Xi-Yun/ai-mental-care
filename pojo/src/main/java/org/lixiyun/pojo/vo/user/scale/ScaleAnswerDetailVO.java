package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 答题明细 VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "答题明细 VO")
public class ScaleAnswerDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID", example = "201")
    private Long questionId;

    @Schema(description = "题干快照（作答当时版本）")
    private String questionTitle;

    @Schema(description = "题目类型快照：1-单选 2-多选 3-填空", example = "1")
    private Integer questionType;

    @Schema(description = "作答状态：0-未作答 1-已作答", example = "1")
    private Integer answerStatus;

    @Schema(description = "填空/简答文本快照")
    private String answerText;

    @Schema(description = "选中的选项快照列表（单选/多选）")
    private List<ScaleSelectedOptionVO> selectedOptions;

    @Schema(description = "答题写入时间")
    private LocalDateTime createdTime;

}