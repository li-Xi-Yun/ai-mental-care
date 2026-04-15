package org.lixiyun.pojo.vo.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 量表类别VO
 * @author lixiyun
 * @since 2026-04-15
 */
@Schema(description = "量表类别VO")
@Data
public class ScaleCategoryVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "类别名称")
    private String categoryName;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "创建人")
    private Long createdBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "更新人")
    private Long updatedBy;
}
