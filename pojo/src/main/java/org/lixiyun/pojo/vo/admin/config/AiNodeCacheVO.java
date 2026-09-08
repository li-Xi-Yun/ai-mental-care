package org.lixiyun.pojo.vo.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * AI节点配置缓存状态VO
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Schema(description = "AI节点配置缓存状态VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiNodeCacheVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "缓存中节点数量", example = "8")
    private Integer cacheSize;

    @Schema(description = "缓存中节点唯一标识列表", example = "[\"psychologicalState\",\"riskAssessment\"]")
    private List<String> nodeKeys;
}