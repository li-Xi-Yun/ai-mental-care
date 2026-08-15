package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表选项模板VO
 * @author lixiyun
 * @since 2026-04-20
 */
@Schema(description = "量表选项模板VO")
@Data
public class ScaleOptionTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "自增主键ID")
    private Long id;

    @Schema(description = "量表ID")
    private Long scaleId;

    @Schema(description = "选项描述")
    private String optionText;

    @Schema(description = "该选项对应的原始分数")
    private Integer score;

    @Schema(description = "选项显示顺序")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "创建人用户ID")
    private Long createdBy;

    @Schema(description = "最后更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "最后更新人用户ID")
    private Long updatedBy;
}
