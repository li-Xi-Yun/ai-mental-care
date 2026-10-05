package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 选项修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "选项修改DTO")
public class AdminScaleOptionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "选项文本不能为空")
    @Schema(description = "选项文本", requiredMode = Schema.RequiredMode.REQUIRED, example = "没有")
    private String optionText;

    @Schema(description = "选项对应的原始分数", example = "0")
    private BigDecimal score;

    @Schema(description = "选项显示顺序", example = "0")
    private Integer sort;
}