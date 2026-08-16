package org.lixiyun.pojo.dto.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 语音消息发送请求DTO
 *
 * @author lixiyun
 * @since 2026-08-15 10:00
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "语音消息发送请求")
public class AudioMessageSendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "用户语音消息不能为空")
    @Schema(description = "用户语音消息内容", example = "音频数据", requiredMode = Schema.RequiredMode.REQUIRED)
    private byte[] audioMessage;

    @Schema(description = "会话ID（可选），第一次对话时不需要发送", example = "2412321342421", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long conversationId;

}