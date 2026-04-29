package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-04-15 18:42
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "选项ID")
    private Long id;

    @Schema(description = "选项描述")
    @NotBlank(message = "选项内容不能为空")
    private String optionText;

    @Schema(description = "选项对应的分数")
    @NotNull(message = "选项分数不能为空")
    private Integer score;

    @Schema(description = "选项显示顺序（前端传递）")
    @NotNull(message = "选项顺序不能为空")
    private Integer sort;
}