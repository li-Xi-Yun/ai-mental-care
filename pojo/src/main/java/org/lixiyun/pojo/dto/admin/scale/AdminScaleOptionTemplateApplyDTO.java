package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 选项模板应用到题目DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "选项模板应用到题目DTO")
public class AdminScaleOptionTemplateApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "题目ID列表不能为空")
    @Schema(description = "待应用模板的题目ID列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2,3]")
    private List<Long> questionIds;

    @Schema(description = "应用模式：REPLACE=覆盖写 APPEND=追加写", example = "REPLACE")
    private String mode;
}