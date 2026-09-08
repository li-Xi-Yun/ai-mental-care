package org.lixiyun.pojo.vo.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI节点配置简要VO（分页列表展示，不含提示词全文）
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Schema(description = "AI节点配置简要VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiNodeConfigSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "自增主键ID", example = "1")
    private Long id;

    @Schema(description = "节点唯一标识", example = "psychologicalState")
    private String nodeKey;

    @Schema(description = "节点中文名称", example = "心理状态与症状评估")
    private String nodeName;

    @Schema(description = "节点分组", example = "process")
    private String nodeGroup;

    @Schema(description = "默认使用的模型类型：0-OLLAMA 1-DEEP_SEEK 2-DASH_SCOPE", example = "1")
    private Integer modelType;

    @Schema(description = "是否启用：0-禁用 1-启用", example = "1")
    private Integer enabled;

    @Schema(description = "节点显示排序序号", example = "1")
    private Integer sort;

    @Schema(description = "配置版本号", example = "1")
    private Integer version;

    @Schema(description = "最后更新时间", example = "2026-09-07 12:00:00")
    private LocalDateTime updatedTime;

    @Schema(description = "备注信息", example = "心理状态评估节点")
    private String remark;
}