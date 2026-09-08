package org.lixiyun.pojo.vo.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI节点配置变更历史VO
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Schema(description = "AI节点配置变更历史VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiNodeConfigHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "历史记录ID", example = "1")
    private Long id;

    @Schema(description = "关联配置ID", example = "1")
    private Long configId;

    @Schema(description = "节点唯一标识", example = "psychologicalState")
    private String nodeKey;

    @Schema(description = "修改前的系统提示词")
    private String oldSystemPrompt;

    @Schema(description = "修改后的系统提示词")
    private String newSystemPrompt;

    @Schema(description = "修改前的模型类型：0-OLLAMA 1-DEEP_SEEK 2-DASH_SCOPE", example = "1")
    private Integer oldModelType;

    @Schema(description = "修改后的模型类型：0-OLLAMA 1-DEEP_SEEK 2-DASH_SCOPE", example = "0")
    private Integer newModelType;

    @Schema(description = "修改前的推理参数快照")
    private Map<String, Object> oldParams;

    @Schema(description = "修改后的推理参数快照")
    private Map<String, Object> newParams;

    @Schema(description = "修改前的版本号", example = "1")
    private Integer oldVersion;

    @Schema(description = "修改后的版本号", example = "2")
    private Integer newVersion;

    @Schema(description = "变更摘要", example = "修改了系统提示词")
    private String changeSummary;

    @Schema(description = "变更时间", example = "2026-09-07 12:00:00")
    private LocalDateTime createdTime;

    @Schema(description = "变更操作人用户ID", example = "1")
    private Long createdBy;
}