package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;

/**
 * 量表类别分页查询DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表类别分页查询DTO")
public class AdminScaleCategoryQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "类别名称（支持模糊匹配）", example = "心理健康")
    private String categoryName;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "删除状态：0=未删除 1=已删除", example = "0")
    private Integer deletedFlag;
}