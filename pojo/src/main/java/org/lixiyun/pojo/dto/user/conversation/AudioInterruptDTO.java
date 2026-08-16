package org.lixiyun.pojo.dto.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 语音中断请求DTO
 *
 * @author lixiyun
 * @since 2026-08-15 10:05
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "语音中断请求")
public class AudioInterruptDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "会话ID不能为空")
    @Schema(description = "会话ID", example = "2412321342421", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long conversationId;

}