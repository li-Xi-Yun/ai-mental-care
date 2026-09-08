package org.lixiyun.pojo.dto.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;

/**
 * AI节点配置分页查询DTO
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Data
@Schema(description = "AI节点配置分页查询DTO")
public class AdminAiNodeConfigQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点唯一标识（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "psychologicalState")
    private String nodeKey;

    @Schema(description = "节点中文名称（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "心理状态")
    private String nodeName;

    @Schema(description = "节点分组：process/input/knowledge", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "process")
    private String nodeGroup;

    @NumberOfRanges(min = 0, max = 2)
    @Schema(description = "模型类型：0-OLLAMA 1-DEEP_SEEK 2-DASH_SCOPE", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer modelType;

    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "是否启用：0-禁用 1-启用", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "1")
    private Integer enabled;
}