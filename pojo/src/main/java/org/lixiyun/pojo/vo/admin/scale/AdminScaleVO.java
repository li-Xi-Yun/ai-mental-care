package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 量表主表VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表主表VO")
public class AdminScaleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表ID", example = "1")
    private Long id;

    @Schema(description = "量表名称", example = "抑郁自评量表")
    private String scaleName;

    @Schema(description = "量表分类ID", example = "1")
    private Long scaleCategoryId;

    @Schema(description = "量表分类名称", example = "心理健康")
    private String scaleCategoryName;

    @Schema(description = "状态：0=禁用 1=启用", example = "1")
    private Integer status;

    @Schema(description = "是否允许重复作答：0=否 1=是", example = "1")
    private Integer allowRepeat;

    @Schema(description = "重复作答冷却时间(分钟)", example = "1440")
    private Integer coolMinutes;

    @Schema(description = "作答限时（秒），NULL不限时", example = "600")
    private Integer timeLimit;

    @Schema(description = "是否匿名测评：0=否 1=是", example = "0")
    private Integer anonymous;

    @Schema(description = "当前生效版本ID", example = "1")
    private Long currentVersionId;

    @Schema(description = "当前生效版本号", example = "v1.0")
    private String currentVersionNo;

    @Schema(description = "当前版本题目数", example = "20")
    private Integer questionCount;

    @Schema(description = "当前版本维度数", example = "2")
    private Integer dimensionCount;

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