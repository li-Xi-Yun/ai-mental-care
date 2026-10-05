package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 题目类型统计 VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "题目类型统计 VO")
public class ScaleQuestionTypeStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "题目类型：1-单选 2-多选 3-填空", example = "1")
    private Integer questionType;

    @Schema(description = "题目类型名称", example = "单选")
    private String questionTypeLabel;

    @Schema(description = "该类型题目数量", example = "15")
    private Integer count;

}
