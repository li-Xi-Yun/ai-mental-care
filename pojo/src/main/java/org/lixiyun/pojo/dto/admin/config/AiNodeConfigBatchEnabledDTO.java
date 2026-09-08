package org.lixiyun.pojo.dto.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;
import java.util.List;

/**
 * AI节点配置批量启用/禁用DTO
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Data
@Schema(description = "AI节点配置批量启用/禁用DTO")
public class AiNodeConfigBatchEnabledDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "节点唯一标识列表不能为空")
    @Schema(description = "节点唯一标识列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"psychologicalState\",\"riskAssessment\"]")
    private List<String> nodeKeys;

    @NotNull(message = "启用状态不能为空")
    @NumberOfRanges(min = 0, max = 1)
    @Schema(description = "是否启用：0-禁用 1-启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer enabled;
}