package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 单次测评结果详情 VO（在 {@link ScaleRecordVO} 基础上增加版本、匿名与维度得分）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "单次测评结果详情 VO")
public class ScaleRecordDetailVO extends ScaleRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当时作答版本号", example = "v1.0")
    private String versionNo;

    @Schema(description = "该次测评是否匿名：0-否 1-是", example = "0")
    private Integer anonymous;

    @Schema(description = "维度得分与解读列表（库内快照）")
    private List<ScaleDimensionResultVO> dimensionResults;

}