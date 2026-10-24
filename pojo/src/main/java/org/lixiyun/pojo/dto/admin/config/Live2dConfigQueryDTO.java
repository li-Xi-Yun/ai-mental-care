package org.lixiyun.pojo.dto.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;

/**
 * Live2D模型配置分页查询DTO
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Data
@Schema(description = "Live2D模型配置分页查询DTO")
public class Live2dConfigQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模型名称（模糊匹配；同时匹配模型别名 alias_name，即关键词命中模型名称或中文别名任一即可）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "hiyori")
    private String modelName;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "是否启用：0-禁用 1-启用", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer enabled;
}