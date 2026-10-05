package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 量表列表展示 VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表列表展示 VO")
public class ScaleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表ID", example = "1")
    private Long id;

    @Schema(description = "量表名称", example = "抑郁自评量表")
    private String scaleName;

    @Schema(description = "类别ID", example = "1")
    private Long scaleCategoryId;

    @Schema(description = "是否允许重复作答：0-否 1-是", example = "1")
    private Integer allowRepeat;

    @Schema(description = "重复作答冷却时间（分钟），allowRepeat=0 时无意义", example = "0")
    private Integer coolMinutes;

    @Schema(description = "作答限时（秒），NULL 表示不限时", example = "600")
    private Integer timeLimit;

    @Schema(description = "是否匿名测评：0-否 1-是", example = "0")
    private Integer anonymous;

    @Schema(description = "当前生效版本号", example = "v1.0")
    private String versionNo;

    @Schema(description = "量表说明/指导语（取自当前版本）")
    private String description;

    @Schema(description = "当前版本题目总数（派生）", example = "20")
    private Integer questionCount;

    @Schema(description = "预估用时（分钟，派生）", example = "10")
    private Integer estimatedMinutes;

}
