package org.lixiyun.pojo.dto.admin.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;

/**
 * 跳题规则新增/修改DTO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "跳题规则新增/修改DTO")
public class AdminScaleBranchRuleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "规则ID（更新时必填）", example = "1")
    private Long id;

    @NotNull(message = "量表版本ID不能为空", groups = {AddGroup.class})
    @Schema(description = "量表版本ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long scaleVersionId;

    @NotNull(message = "触发题ID不能为空", groups = {AddGroup.class})
    @Schema(description = "触发题ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    private Long sourceQuestionId;

    @Schema(description = "触发选项ID（选中该选项触发）", example = "18")
    private Long sourceOptionId;

    @Schema(description = "跳转目标题ID，NULL表示结束测评", example = "10")
    private Long targetQuestionId;
}