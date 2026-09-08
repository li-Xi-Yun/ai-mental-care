package org.lixiyun.pojo.vo.admin.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI节点提示词VO
 *
 * @author lixiyun
 * @since 2026-09-07
 */
@Schema(description = "AI节点提示词VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiNodePromptVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点唯一标识", example = "psychologicalState")
    private String nodeKey;

    @Schema(description = "系统提示词", example = "你是一个心理健康领域的评估助手……")
    private String systemPrompt;

    @Schema(description = "配置版本号", example = "1")
    private Integer version;
}