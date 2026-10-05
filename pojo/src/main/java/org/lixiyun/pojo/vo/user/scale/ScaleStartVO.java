package org.lixiyun.pojo.vo.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 开始测评返回VO（前台），一次下发整卷题目与精简跳题规则
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "开始测评返回VO")
public class ScaleStartVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "测评记录ID", example = "1")
    private Long recordId;

    @Schema(description = "量表ID", example = "1")
    private Long scaleId;

    @Schema(description = "本次作答锁定的版本ID", example = "1")
    private Long scaleVersionId;

    @Schema(description = "量表名称", example = "一般健康问卷")
    private String scaleName;

    @Schema(description = "版本号", example = "v1.0")
    private String versionNo;

    @Schema(description = "指导语", example = "请根据最近两周的实际情况作答")
    private String description;

    @Schema(description = "版权说明（仅本接口返回一次）", example = "本量表版权归XXXX所有")
    private String copyrightInfo;

    @Schema(description = "作答限时（秒），NULL不限时", example = "600")
    private Integer timeLimit;

    @Schema(description = "是否匿名测评", example = "0")
    private Integer anonymous;

    @Schema(description = "总题数", example = "10")
    private Integer totalQuestionCount;

    @Schema(description = "题目与选项（不含任何分数信息）")
    private List<ScaleQuestionPayloadVO> questions;

    @Schema(description = "精简跳题规则（仅作答内有效）")
    private List<ScaleBranchRulePayloadVO> branchRules;

}