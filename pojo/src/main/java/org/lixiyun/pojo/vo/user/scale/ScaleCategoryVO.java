package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 量表类别VO（前台）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@Schema(description = "量表类别VO")
public class ScaleCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "类别ID", example = "1")
    private Long id;

    @Schema(description = "类别名称", example = "焦虑类")
    private String categoryName;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "该类别下启用且有当前版本的量表数量", example = "3")
    private Long scaleCount;
}