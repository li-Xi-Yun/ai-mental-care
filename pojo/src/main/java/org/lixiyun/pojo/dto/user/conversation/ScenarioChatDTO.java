package org.lixiyun.pojo.dto.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

import java.io.Serializable;

/**
 * 场景化对话DTO
 *
 * @author lixiyun
 * @since 2026-04-21
 */
@Data
@Builder
@Schema(description = "场景化对话DTO")
public class ScenarioChatDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "用户输入的消息内容", example = "我最近压力好大", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "会话ID，第一次对话时不需要发送", example = "2412321342421", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long conversationId;

    @NotNull
    @NumberOfRanges
    @Schema(description = "当前对话轮次（前端需在上一次轮次基础上加一），第一次对话传1", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer currentRound;

    @NotNull
    @Schema(description = "对话场景类型：0-日常闲聊，1-情绪纾解、2-助眠陪伴、3-考前减压、4-职场调适、5-亲密沟通、6-自信赋能",
            example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NumberOfRanges(max = 6)
    private Integer scenario;

}
