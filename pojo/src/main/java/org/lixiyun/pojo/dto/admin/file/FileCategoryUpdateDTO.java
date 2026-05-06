package org.lixiyun.pojo.dto.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 文件分类修改DTO
 *
 * @author lixiyun
 * @since 2026-05-01
 */
@Schema(description = "文件分类修改DTO")
@Data
public class FileCategoryUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分类ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分类ID不能为空")
    private Long id;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    private String categoryName;
}
