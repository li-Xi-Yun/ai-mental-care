package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;

/**
 * 量表维度新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表维度新增/修改DTO")
public class AdminScaleDimensionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "维度ID（更新时必填）", example = "1")
    private Long id;

    @NotNull(message = "量表版本ID不能为空", groups = {AddGroup.class})
    @Schema(description = "量表版本ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long scaleVersionId;

    @NotBlank(message = "维度名称不能为空", groups = {AddGroup.class})
    @Schema(description = "维度名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "抑郁")
    private String dimName;

    @NotBlank(message = "维度编码不能为空", groups = {AddGroup.class})
    @Schema(description = "维度编码（程序计分用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEP")
    private String dimCode;

    @Schema(description = "维度说明", example = "评估抑郁情绪的维度")
    private String dimDesc;

    @Schema(description = "排序", example = "0")
    private Integer sort;
}