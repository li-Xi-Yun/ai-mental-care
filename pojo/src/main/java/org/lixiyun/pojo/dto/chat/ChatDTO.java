package org.lixiyun.pojo.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-03-16 12:55
 */
@Data
@Builder
public class ChatDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "用户输出的语句", example = "你是谁", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "会话ID，第一次对话时，不需要发送", example = "2412321342421", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long conversationId;

    @NotNull
    @NumberOfRanges
    @Schema(description = "本次的对话轮次（前端传来时，需要在上一次的对话轮次加一后传来），第一次对话时，前端直接传1", example = "5", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer currentRound;

}
