package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 选项排序项DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "选项排序项DTO")
public class AdminScaleOptionSortItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "选项ID不能为空")
    @Schema(description = "选项ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long optionId;

    @NotNull(message = "排序不能为空")
    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer sort;
}