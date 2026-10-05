package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表版本VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表版本VO")
public class AdminScaleVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "版本ID", example = "1")
    private Long id;

    @Schema(description = "量表ID", example = "1")
    private Long scaleId;

    @Schema(description = "版本号", example = "v1.0")
    private String versionNo;

    @Schema(description = "量表说明、指导语", example = "请根据最近一周实际情况作答")
    private String description;

    @Schema(description = "量表版权/授权说明", example = "© 2026 某机构")
    private String copyrightInfo;

    @Schema(description = "版本题目数", example = "20")
    private Integer questionCount;

    @Schema(description = "版本维度数", example = "2")
    private Integer dimensionCount;

    @Schema(description = "是否当前生效版本", example = "true")
    private Boolean isCurrent;

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