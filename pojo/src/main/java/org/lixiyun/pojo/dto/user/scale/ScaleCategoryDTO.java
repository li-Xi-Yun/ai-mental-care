package org.lixiyun.pojo.dto.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 量表类别DTO
 * @author lixiyun
 * @since 2026-04-15
 */
@Schema(description = "量表类别DTO")
@Data
public class ScaleCategoryDTO  implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "类别名称")
    @NotBlank(message = "类别名称不能为空")
    private String categoryName;
}
