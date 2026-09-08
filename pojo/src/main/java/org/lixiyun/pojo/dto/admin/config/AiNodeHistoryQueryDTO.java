package org.lixiyun.pojo.dto.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.lixiyun.pojo.dto.base.PageBaseDTO;

import java.io.Serializable;

/**
 * AI节点配置变更历史分页查询DTO
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Data
@Schema(description = "AI节点配置变更历史分页查询DTO")
public class AiNodeHistoryQueryDTO extends PageBaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点唯一标识（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "psychologicalState")
    private String nodeKey;

    @Schema(description = "变更摘要（支持模糊匹配）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "修改了系统提示词")
    private String changeSummary;
}