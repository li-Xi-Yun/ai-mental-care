package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 常模组新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "常模组新增/修改DTO")
public class AdminScaleNormGroupDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "常模组ID（更新时必填）", example = "1")
    private Long id;

    @NotNull(message = "量表版本ID不能为空", groups = {AddGroup.class})
    @Schema(description = "量表版本ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long scaleVersionId;

    @Schema(description = "维度ID，NULL代表总分常模", example = "1")
    private Long dimensionId;

    @NotBlank(message = "常模组名称不能为空", groups = {AddGroup.class})
    @Schema(description = "常模组名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "全国成年男性常模")
    private String groupName;

    @Schema(description = "常模组编码，方便程序查找", example = "ADULT_MALE")
    private String groupCode;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "性别：1=男 0=女 NULL=不限", example = "1")
    private Integer gender;

    @Schema(description = "最小年龄（包含）", example = "18")
    private Integer ageMin;

    @Schema(description = "最大年龄（包含）", example = "60")
    private Integer ageMax;

    @Schema(description = "学历：1=初中 2=高中 3=大专 4=本科 5=硕士 6=博士 NULL=不限", example = "4")
    private Integer education;

    @Schema(description = "职业群体 NULL=不限", example = "3")
    private Integer occupation;

    @Schema(description = "地区 NULL=不限", example = "1")
    private Integer region;

    @NotNull(message = "常模计算方式不能为空")
    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "常模计算方式：0=公式法 1=查表法", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer normType;

    @Schema(description = "原始分均值M", example = "32.5")
    private BigDecimal mean;

    @Schema(description = "原始分标准差SD", example = "6.8")
    private BigDecimal sd;

    @Schema(description = "常模来源/参考文献", example = "XX量表使用手册")
    private String source;

    @Schema(description = "常模制定年份", example = "2020")
    private Integer normYear;

    @Schema(description = "排序：默认常模排在最前", example = "0")
    private Integer sort;
}