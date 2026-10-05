package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选项模板项VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "选项模板项VO")
public class AdminScaleOptionTemplateItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模板项ID", example = "1")
    private Long id;

    @Schema(description = "所属模板组ID", example = "1")
    private Long templateGroupId;

    @Schema(description = "选项文本", example = "从不")
    private String optionText;

    @Schema(description = "选项对应的原始分数", example = "1")
    private BigDecimal score;

    @Schema(description = "选项显示顺序", example = "0")
    private Integer sort;

    @Schema(description = "创建时间", example = "2026-10-05 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间", example = "2026-10-05 10:00:00")
    private LocalDateTime updatedTime;
}