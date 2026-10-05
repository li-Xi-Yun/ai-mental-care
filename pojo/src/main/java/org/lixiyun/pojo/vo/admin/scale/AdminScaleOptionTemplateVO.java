package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 选项模板组VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "选项模板组VO")
public class AdminScaleOptionTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模板组ID", example = "1")
    private Long id;

    @Schema(description = "量表版本ID", example = "1")
    private Long scaleVersionId;

    @Schema(description = "模板名称", example = "频率五级选项")
    private String templateName;

    @Schema(description = "模板描述", example = "从不/偶尔/有时/经常/总是")
    private String templateDesc;

    @Schema(description = "模板选项明细列表")
    private List<AdminScaleOptionTemplateItemVO> items;

    @Schema(description = "创建时间", example = "2026-10-05 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间", example = "2026-10-05 10:00:00")
    private LocalDateTime updatedTime;

    @Schema(description = "删除状态：0=未删除 1=已删除", example = "0")
    private Integer deletedFlag;
}