package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户答题明细VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户答题明细VO")
public class AdminScaleAnswerDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID", example = "1")
    private Long questionId;

    @Schema(description = "题干快照", example = "我感到情绪低落")
    private String questionTitle;

    @Schema(description = "题目类型快照：1=单选 2=多选 3=填空", example = "1")
    private Integer questionType;

    @Schema(description = "答题状态：0=未作答 1=已作答", example = "1")
    private Integer answerStatus;

    @Schema(description = "填空答案文本（仅填空题返回）", example = "经常失眠")
    private String answerText;

    @Schema(description = "选中选项快照列表")
    private List<AdminScaleSelectedOptionVO> selectedOptions;

    @Schema(description = "答题记录创建时间", example = "2026-10-05 10:20:00")
    private LocalDateTime createdTime;
}