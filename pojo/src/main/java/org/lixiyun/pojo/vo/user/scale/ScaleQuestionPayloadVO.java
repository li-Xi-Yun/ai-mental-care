package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 题目载荷 VO（作答下发题目，不含任何分数信息）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "题目载荷 VO")
public class ScaleQuestionPayloadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID", example = "201")
    private Long questionId;

    @Schema(description = "题干", example = "近两周我经常感到情绪低落")
    private String title;

    @Schema(description = "题目类型：1-单选 2-多选 3-填空", example = "1")
    private Integer questionType;

    @Schema(description = "是否必答：0-否 1-是", example = "1")
    private Integer required;

    @Schema(description = "题目顺序", example = "1")
    private Integer sort;

    @Schema(description = "所属维度ID（可空，前端用于分区/分节展示）")
    private Long dimensionId;

    @Schema(description = "选项列表（填空题为空）")
    private List<ScaleOptionPayloadVO> options;

}