package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 维度得分结果 VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "维度得分结果 VO")
public class ScaleDimensionResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "维度ID", example = "1")
    private Long dimensionId;

    @Schema(description = "维度名称", example = "抑郁")
    private String dimName;

    @Schema(description = "维度说明")
    private String dimDesc;

    @Schema(description = "维度原始分", example = "12.00")
    private BigDecimal dimScore;

    @Schema(description = "维度解读快照")
    private String dimResult;

    @Schema(description = "该维度风险等级：0-无 1-低 2-中 3-高", example = "0")
    private Integer riskLevel;

}