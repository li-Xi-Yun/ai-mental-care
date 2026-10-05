package org.lixiyun.pojo.vo.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI节点标识VO（下拉选项使用）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Schema(description = "AI节点标识VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiNodeKeyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点配置ID", example = "1")
    private Long id;

    @Schema(description = "节点唯一标识", example = "psychologicalState")
    private String nodeKey;

    @Schema(description = "节点中文名称", example = "心理状态与症状评估")
    private String nodeName;

    @Schema(description = "节点分组", example = "process")
    private String nodeGroup;
}