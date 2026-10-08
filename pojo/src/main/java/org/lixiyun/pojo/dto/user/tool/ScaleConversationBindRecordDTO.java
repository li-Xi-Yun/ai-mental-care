package org.lixiyun.pojo.dto.user.tool;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI对话量表-保存用户测评记录ID DTO
 * <p>
 * 用户从对话卡片进入量表答题页并开始作答后，前端拿到 recordId 立即回调本接口，
 * 由后端把 recordId 持久化到 conversation_pending_action.action_data 中。
 * 之后无论用户如何刷新页面，都可通过待处理记录恢复 recordId，保证作答连续性。
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "AI对话量表-保存用户测评记录ID DTO")
public class ScaleConversationBindRecordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "工具ID不能为空")
    @Schema(description = "工具ID（createScalePendingAction 返回的 toolId，即 conversation_pending_action.id）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
    private Long toolId;

    @NotNull(message = "会话ID不能为空")
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123")
    private Long conversationId;

    @NotNull(message = "测评记录ID不能为空")
    @Schema(description = "测评记录ID（scale_user_record.id）", requiredMode = Schema.RequiredMode.REQUIRED, example = "789")
    private Long recordId;

}
