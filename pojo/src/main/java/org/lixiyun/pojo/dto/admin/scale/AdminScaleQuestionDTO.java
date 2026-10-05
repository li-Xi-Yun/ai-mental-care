package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;
import java.util.List;

/**
 * 量表题目新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表题目新增/修改DTO")
public class AdminScaleQuestionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID（更新时必填）", example = "1")
    private Long id;

    @NotNull(message = "量表版本ID不能为空", groups = {AddGroup.class})
    @Schema(description = "量表版本ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long scaleVersionId;

    @Schema(description = "所属维度ID（筛选题可为空）", example = "1")
    private Long dimensionId;

    @NotBlank(message = "题干不能为空", groups = {AddGroup.class})
    @Schema(description = "题干", requiredMode = Schema.RequiredMode.REQUIRED, example = "我感到情绪低落")
    private String title;

    @NotNull(message = "题目类型不能为空", groups = {AddGroup.class})
    @NumberOfRanges(min = 1, max = 3)
    @Schema(description = "题目类型：1=单选 2=多选 3=填空", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer questionType;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @NumberOfRanges(min = 0, max = 2)
    @Schema(description = "计分方式：1=正向计分 2=反向计分 0=不计分", example = "1")
    private Integer scoreType;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "是否必答：0=否 1=是", example = "1")
    private Integer required;

    @Schema(description = "题目选项列表（单选/多选时使用）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<AdminScaleOptionItemDTO> options;
}