package org.lixiyun.pojo.dto.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-03-24 14:11
 */
@Data
@Builder
@Schema(description = "音频对话DTO")
public class AudioModelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话ID，只有第一次对话时，不需要发送", example = "2412321342421", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long conversationId;

    @NotNull
    @NumberOfRanges
    @Schema(description = "本次的对话轮次（前端传来时，需要在上一次的对话轮次加一后传来），第一次对话时，前端直接传1", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer currentRound;

}
