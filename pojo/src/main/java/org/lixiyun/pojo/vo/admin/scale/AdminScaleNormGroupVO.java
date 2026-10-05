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
 * 常模组VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "常模组VO")
public class AdminScaleNormGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "常模组ID", example = "1")
    private Long id;

    @Schema(description = "量表版本ID", example = "1")
    private Long scaleVersionId;

    @Schema(description = "维度ID，NULL代表总分常模", example = "1")
    private Long dimensionId;

    @Schema(description = "维度名称", example = "抑郁")
    private String dimensionName;

    @Schema(description = "常模组名称", example = "全国成年男性常模")
    private String groupName;

    @Schema(description = "常模组编码", example = "ADULT_MALE")
    private String groupCode;

    @Schema(description = "性别：1=男 0=女 NULL=不限", example = "1")
    private Integer gender;

    @Schema(description = "最小年龄（包含）", example = "18")
    private Integer ageMin;

    @Schema(description = "最大年龄（包含）", example = "60")
    private Integer ageMax;

    @Schema(description = "学历 NULL=不限", example = "4")
    private Integer education;

    @Schema(description = "职业群体 NULL=不限", example = "3")
    private Integer occupation;

    @Schema(description = "地区 NULL=不限", example = "1")
    private Integer region;

    @Schema(description = "常模计算方式：0=公式法 1=查表法", example = "0")
    private Integer normType;

    @Schema(description = "原始分均值M", example = "32.5")
    private BigDecimal mean;

    @Schema(description = "原始分标准差SD", example = "6.8")
    private BigDecimal sd;

    @Schema(description = "常模来源/参考文献", example = "XX量表使用手册")
    private String source;

    @Schema(description = "常模制定年份", example = "2020")
    private Integer normYear;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "常模组下明细数", example = "100")
    private Integer normCount;

    @Schema(description = "创建时间", example = "2026-10-05 10:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间", example = "2026-10-05 10:00:00")
    private LocalDateTime updatedTime;
}