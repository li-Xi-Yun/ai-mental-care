package org.lixiyun.pojo.vo.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 用户测评记录详情VO（在 {@link AdminScaleRecordVO} 基础上增加维度得分）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户测评记录详情VO")
public class AdminScaleRecordDetailVO extends AdminScaleRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "各维度得分结果")
    private List<AdminScaleDimScoreVO> dimensionResults;
}