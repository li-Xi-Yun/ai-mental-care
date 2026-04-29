package org.lixiyun.pojo.dto.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;

import java.io.Serializable;
import java.util.List;

/**
 * 量表题目DTO
 * 对应数据库表：scale_question
 * 仅接收前端传递的核心业务字段
 * @author lixiyun
 * @since 2026-04-15 08:11
 */
@Schema(description = "量表题目DTO")
@Data
public class ScaleQuestionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表id（创建时必传）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @NotNull(message = "量表id不能为空", groups = AddGroup.class)
    private Long scaleId;

    @Schema(description = "题目内容")
    @NotBlank(message = "题目内容不能为空")
    private String title;

    @Schema(description = "题目显示顺序（前端传递）")
    @NotNull(message = "题目顺序不能为空")
    private Integer sort;

    @Schema(description = "计分类型 1=正向 2=反向", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer scoreType = 1;

    @Schema(description = "选项列表（每题必须有选项）")
    @NotEmpty(message = "题目选项不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private List<ScaleOptionDTO> optionList;
}