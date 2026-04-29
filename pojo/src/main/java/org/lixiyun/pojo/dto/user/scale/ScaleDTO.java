package org.lixiyun.pojo.dto.user.scale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.lixiyun.common.validation.group.AddGroup;

import java.io.Serializable;

/**
 * 量表DTO
 * 用于创建和更新量表
 * @author lixiyun
 * @since 2026-04-15
 */
@Schema(description = "量表DTO")
@Data
public class ScaleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "量表名称")
    @NotBlank(message = "量表名称不能为空", groups = {AddGroup.class})
    private String scaleName;

    @Schema(description = "量表说明/指导语", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;

    @Schema(description = "量表分类ID", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long scaleCategoryId;

    @Schema(description = "状态 1=启用 0=禁用", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer status;

    @Schema(description = "统一选项模板的通用评分规则", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String scoreRule;
}
