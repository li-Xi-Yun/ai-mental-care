package org.lixiyun.pojo.dto.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;

import java.io.Serializable;

/**
 * 量表选项模板DTO
 * @author lixiyun
 * @since 2026-04-20
 */
@Schema(description = "量表选项模板DTO")
@Data
public class ScaleOptionTemplateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表ID")
    @NotNull(message = "量表ID不能为空", groups = {AddGroup.class})
    private Long scaleId;

    @Schema(description = "选项模板ID")
    @NotNull(message = "选项模板ID不能为空", groups = {UpdateGroup.class})
    private Long id;

    @Schema(description = "选项描述")
    @NotBlank(message = "选项描述不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private String optionText;

    @Schema(description = "该选项对应的原始分数")
    @NotNull(message = "选项分数不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private Integer score;

    @Schema(description = "选项显示顺序")
    @NotNull(message = "选项顺序不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private Integer sort;
}
