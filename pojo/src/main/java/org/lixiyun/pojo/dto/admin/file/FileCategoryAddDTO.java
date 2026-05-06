package org.lixiyun.pojo.dto.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 文件分类新增DTO
 *
 * @author lixiyun
 * @since 2026-05-01
 */
@Schema(description = "文件分类新增DTO")
@Data
public class FileCategoryAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "父级分类ID（可选，为空表示一级分类）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long parentId;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    private String categoryName;
}
