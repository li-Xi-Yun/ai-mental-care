package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 量表详情VO（前台），在列表展示字段基础上增加维度构成与题型统计
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "量表详情VO")
public class ScaleDetailVO extends ScaleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前版本主要维度名称列表", example = "[\"抑郁\",\"焦虑\"]")
    private List<String> dimensions;

    @Schema(description = "当前版本各题型数量统计")
    private List<ScaleQuestionTypeStatVO> questionTypes;

}