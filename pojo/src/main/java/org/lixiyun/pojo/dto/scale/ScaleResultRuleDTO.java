package org.lixiyun.pojo.dto.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.group.AddGroup;
import org.lixiyun.common.validation.group.UpdateGroup;

import java.io.Serializable;

/**
 * 自定义量表 - 结果规则DTO
 * 对应数据库表：scale_result_rule
 * 仅接收前端传递的核心业务字段
 * @author lixiyun
 * @since 2026-04-15 08:11
 */
@Schema(description = "自定义量表 - 结果规则DTO")
@Data
public class ScaleResultRuleDTO  implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表ID")
    @NotNull(message = "量表ID不能为空", groups = AddGroup.class)
    private Long scaleId;

    @Schema(description = "分数区间最小值（包含）")
    @NotNull(message = "规则最低分数不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private Integer minScore;

    @Schema(description = "分数区间最大值（包含）")
    @NotNull(message = "规则最高分数不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private Integer maxScore;

    @Schema(description = "该分数区间对应的结果描述文本")
    @NotBlank(message = "测评结果描述不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private String resultText;

}