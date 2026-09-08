package org.lixiyun.pojo.dto.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * AI节点提示词修改DTO
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Data
@Schema(description = "AI节点提示词修改DTO")
public class AiNodePromptDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "系统提示词不能为空")
    @Schema(description = "系统提示词", requiredMode = Schema.RequiredMode.REQUIRED, example = "你是一个心理健康领域的评估助手……")
    private String systemPrompt;

    @NotNull(message = "配置版本号不能为空")
    @Schema(description = "配置版本号（乐观锁，修改时必传）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer version;
}