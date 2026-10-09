package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话待处理人工交互 VO（前端卡片展示用，不含 tool_result 内部数据）
 *
 * @author lixiyun
 * @since 2026-10-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话待处理人工交互 VO")
public class ConversationPendingActionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "待处理记录ID", example = "42")
    private Long id;

    @Schema(description = "交互类型：1=量表测评 2=转介咨询师 3=紧急确认", example = "1")
    private Integer actionType;

    @Schema(description = "创建时的对话轮次，前端据此定位卡片插入位置", example = "10")
    private Integer roundNum;

    @Schema(description = "前端渲染卡片所需的上下文数据（已解析为JSON对象，结构因action_type而异）")
    private Object actionData;

    @Schema(description = "状态：0=等待用户操作 1=待对话注入 2=已响应 3=已取消 4=已过期", example = "0")
    private Integer status;

    @Schema(description = "过期时间；NULL=永不过期")
    private LocalDateTime expireTime;

    @Schema(description = "完成/取消时间")
    private LocalDateTime completedTime;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

}
