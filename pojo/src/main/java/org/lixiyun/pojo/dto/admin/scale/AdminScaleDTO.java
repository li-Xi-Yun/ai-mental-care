package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;

/**
 * 量表主表新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "量表主表新增/修改DTO")
public class AdminScaleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "量表名称不能为空", groups = {AddGroup.class})
    @Schema(description = "量表名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "抑郁自评量表")
    private String scaleName;

    @Schema(description = "量表分类ID", example = "1")
    private Long scaleCategoryId;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "状态：0=禁用 1=启用", example = "1")
    private Integer status;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "是否允许重复作答：0=否 1=是", example = "1")
    private Integer allowRepeat;

    @Schema(description = "重复作答冷却时间(分钟)", example = "1440")
    private Integer coolMinutes;

    @Schema(description = "作答限时（秒），NULL不限时", example = "600")
    private Integer timeLimit;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "是否匿名测评：0=否 1=是", example = "0")
    private Integer anonymous;

    @Schema(description = "是否同时创建首个版本（true=创建并发布为当前版本）", example = "true")
    private Boolean createFirstVersion;

    @Schema(description = "首个版本号（创建首版时使用）", example = "v1.0")
    private String versionNo;

    @Schema(description = "量表说明、指导语", example = "请根据最近一周实际情况作答")
    private String description;

    @Schema(description = "量表版权/授权说明", example = "© 2026 某机构")
    private String copyrightInfo;
}