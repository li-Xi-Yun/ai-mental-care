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
 * 量表题目VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表题目VO")
public class AdminScaleQuestionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID", example = "1")
    private Long id;

    @Schema(description = "量表版本ID", example = "1")
    private Long scaleVersionId;

    @Schema(description = "所属维度ID", example = "1")
    private Long dimensionId;

    @Schema(description = "所属维度名称", example = "抑郁")
    private String dimensionName;

    @Schema(description = "题干", example = "我感到情绪低落")
    private String title;

    @Schema(description = "题目类型：1=单选 2=多选 3=填空", example = "1")
    private Integer questionType;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "计分方式：1=正向计分 2=反向计分 0=不计分", example = "1")
    private Integer scoreType;

    @Schema(description = "是否必答：0=否 1=是", example = "1")
    private Integer required;

    @Schema(description = "题目选项列表")
    private List<AdminScaleOptionVO> options;

    @Schema(description = "创建时间", example = "2026-10-05 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间", example = "2026-10-05 10:00:00")
    private LocalDateTime updatedTime;
}