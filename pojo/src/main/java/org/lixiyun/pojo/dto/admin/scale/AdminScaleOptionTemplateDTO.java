package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;
import java.util.List;

/**
 * 选项模板组新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "选项模板组新增/修改DTO")
public class AdminScaleOptionTemplateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模板组ID（更新时必填）", example = "1")
    private Long id;

    @NotNull(message = "量表版本ID不能为空", groups = {AddGroup.class})
    @Schema(description = "量表版本ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long scaleVersionId;

    @NotBlank(message = "模板名称不能为空", groups = {AddGroup.class})
    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "频率五级选项")
    private String templateName;

    @Schema(description = "模板描述", example = "从不/偶尔/有时/经常/总是")
    private String templateDesc;

    @NotEmpty(message = "模板选项明细列表不能为空")
    @Valid
    @Schema(description = "模板选项明细列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AdminScaleOptionTemplateItemDTO> items;
}