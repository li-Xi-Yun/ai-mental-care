package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户测评答题明细VO
 * @author lixiyun
 * @since 2026-04-17
 */
@Schema(description = "用户测评答题明细VO")
@Data
public class ScaleUserAnswerVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "答题明细ID")
    private Long id;

    @Schema(description = "测评记录ID")
    private Long recordId;

    @Schema(description = "题目ID")
    private Long questionId;

    @Schema(description = "答题时的题目内容")
    private String questionTitle;

    @Schema(description = "选项ID")
    private Long optionId;

    @Schema(description = "答题时的选项内容")
    private String optionText;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
