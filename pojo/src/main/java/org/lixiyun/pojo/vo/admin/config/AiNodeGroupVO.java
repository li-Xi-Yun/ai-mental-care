package org.lixiyun.pojo.vo.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI节点分组VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Schema(description = "AI节点分组VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiNodeGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点分组标识", example = "process")
    private String nodeGroup;

    @Schema(description = "该分组下的节点数量", example = "8")
    private Long count;
}