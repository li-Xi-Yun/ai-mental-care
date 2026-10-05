package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 精简跳题规则载荷 VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "精简跳题规则载荷 VO")
public class ScaleBranchRulePayloadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "触发题ID", example = "5")
    private Long sourceQuestionId;

    @Schema(description = "触发选项ID（空表示该题任意作答都触发跳转/结束）")
    private Long sourceOptionId;

    @Schema(description = "跳转目标题ID（空表示结束测评）")
    private Long targetQuestionId;

}