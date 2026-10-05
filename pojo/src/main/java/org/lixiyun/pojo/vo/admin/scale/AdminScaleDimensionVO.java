package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表维度VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表维度VO")
public class AdminScaleDimensionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "维度ID", example = "1")
    private Long id;

    @Schema(description = "量表版本ID", example = "1")
    private Long scaleVersionId;

    @Schema(description = "维度名称", example = "抑郁")
    private String dimName;

    @Schema(description = "维度编码", example = "DEP")
    private String dimCode;

    @Schema(description = "维度说明", example = "评估抑郁情绪的维度")
    private String dimDesc;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "该维度下题目数", example = "10")
    private Integer questionCount;

    @Schema(description = "创建时间", example = "2026-10-05 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间", example = "2026-10-05 10:00:00")
    private LocalDateTime updatedTime;
}