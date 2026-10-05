package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表类别VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表类别VO")
public class AdminScaleCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "类别ID", example = "1")
    private Long id;

    @Schema(description = "类别名称", example = "心理健康")
    private String categoryName;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "该类别下未删除量表数", example = "5")
    private Integer scaleCount;

    @Schema(description = "创建时间", example = "2026-10-05 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "创建人姓名", example = "张三")
    private String createdByName;

    @Schema(description = "最后更新时间", example = "2026-10-05 10:00:00")
    private LocalDateTime updatedTime;

    @Schema(description = "最后更新人姓名", example = "李四")
    private String updatedByName;

    @Schema(description = "删除状态：0=未删除 1=已删除", example = "0")
    private Integer deletedFlag;
}