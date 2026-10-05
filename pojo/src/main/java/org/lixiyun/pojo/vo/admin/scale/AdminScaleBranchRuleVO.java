package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 跳题规则VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "跳题规则VO")
public class AdminScaleBranchRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "规则ID", example = "1")
    private Long id;

    @Schema(description = "量表版本ID", example = "1")
    private Long scaleVersionId;

    @Schema(description = "触发题ID", example = "5")
    private Long sourceQuestionId;

    @Schema(description = "触发题题干", example = "我是否出现过自伤行为")
    private String sourceQuestionTitle;

    @Schema(description = "触发选项ID", example = "18")
    private Long sourceOptionId;

    @Schema(description = "触发选项文本", example = "是")
    private String sourceOptionText;

    @Schema(description = "跳转目标题ID", example = "10")
    private Long targetQuestionId;

    @Schema(description = "跳转目标题题干", example = "最近一次自伤的时间是")
    private String targetQuestionTitle;

    @Schema(description = "创建时间", example = "2026-10-05 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间", example = "2026-10-05 10:00:00")
    private LocalDateTime updatedTime;
}