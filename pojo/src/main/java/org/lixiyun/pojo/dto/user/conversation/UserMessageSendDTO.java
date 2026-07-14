package org.lixiyun.pojo.dto.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户消息发送请求DTO
 *
 * @author lixiyun
 * @since 2026-07-14 16:35
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户消息发送请求")
public class UserMessageSendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户消息内容不能为空")
    @Schema(description = "用户输入的消息内容", example = "我最近压力好大", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "会话ID（可选），第一次对话时不需要发送", example = "2412321342421", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long conversationId;

}