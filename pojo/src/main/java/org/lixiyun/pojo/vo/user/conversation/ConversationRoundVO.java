package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 对话消息按轮次聚合 VO
 * <p>
 * 前端对话历史展示以"轮次"为单位：同一轮次下包含该轮的全部消息，
 * 以及该轮关联的待处理人工交互卡片（如量表卡片）。
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "对话消息按轮次聚合 VO")
public class ConversationRoundVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "轮次", example = "10")
    private Integer roundNum;

    @Schema(description = "该轮次下的全部对话消息（按创建时间正序）")
    private List<ConversationMemoryVO> messages;

    @Schema(description = "该轮次关联的待处理人工交互列表（如量表卡片），无则为空")
    private List<ConversationPendingActionVO> pendingActions;

}
