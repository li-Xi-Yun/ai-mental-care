package org.lixiyun.pojo.dto.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * 对话生命周期初始化请求DTO
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Data
@Schema(description = "对话生命周期初始化请求")
public class ConversationLifecycleInitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话ID（可选），为空时自动创建新会话", example = "2412321342421", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long conversationId;

    @NotEmpty(message = "输入类型不能为空")
    @Schema(description = "输入数据类型集合，如 TEXT、AUDIO", example = "[\"TEXT\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<String> inputTypes;

    @NotEmpty(message = "输出类型不能为空")
    @Schema(description = "输出数据类型集合，如 TEXT、AUDIO", example = "[\"AUDIO\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<String> outputTypes;
}