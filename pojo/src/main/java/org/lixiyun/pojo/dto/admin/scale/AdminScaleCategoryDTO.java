package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;

import java.io.Serializable;

/**
 * 量表类别新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表类别新增/修改DTO")
public class AdminScaleCategoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "类别名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Schema(description = "类别名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "心理健康")
    private String categoryName;

    @Schema(description = "排序", example = "0")
    private Integer sort;
}