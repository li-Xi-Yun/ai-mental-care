package org.lixiyun.pojo.vo.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 量表VO
 * @author lixiyun
 * @since 2026-04-15
 */
@Schema(description = "量表VO")
@Data
public class ScaleVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "量表名称")
    private String scaleName;

    @Schema(description = "量表说明")
    private String description;

    @Schema(description = "题目总数")
    private Integer questionCount;

    @Schema(description = "量表分类ID")
    private Long scaleCategoryId;

    @Schema(description = "状态 1=启用 0=禁用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "创建人")
    private Long createdBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "更新人")
    private Long updatedBy;
}
