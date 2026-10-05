package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 量表版本复制DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表版本复制DTO")
public class AdminScaleVersionCopyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "源版本ID不能为空")
    @Schema(description = "源版本ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long sourceVersionId;

    @NotNull(message = "目标量表ID不能为空")
    @Schema(description = "目标量表ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long targetScaleId;

    @NotBlank(message = "新版本号不能为空")
    @Schema(description = "新版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "v1.1")
    private String versionNo;
}