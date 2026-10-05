package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 批量删除选项DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "批量删除选项DTO")
public class AdminScaleOptionDeleteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "题目ID不能为空")
    @Schema(description = "题目ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long questionId;

    @NotEmpty(message = "选项ID列表不能为空")
    @Schema(description = "选项ID列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2,3]")
    private List<Long> optionIds;
}