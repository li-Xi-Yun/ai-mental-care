package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 选项模板复制DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "选项模板复制DTO")
public class AdminScaleOptionTemplateCopyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "模板组ID不能为空")
    @Schema(description = "模板组ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long groupId;

    @NotNull(message = "目标量表版本ID不能为空")
    @Schema(description = "目标量表版本ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long targetScaleVersionId;
}