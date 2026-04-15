package org.lixiyun.pojo.vo.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 量表结果规则VO
 * @author lixiyun
 * @since 2026-04-15
 */
@Schema(description = "量表结果规则VO")
@Data
public class ScaleResultRuleVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "量表ID")
    private Long scaleId;

    @Schema(description = "最低分")
    private Integer minScore;

    @Schema(description = "最高分")
    private Integer maxScore;

    @Schema(description = "结果描述")
    private String resultText;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "创建人")
    private Long createdBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "更新人")
    private Long updatedBy;
}
