package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author lixiyun
 * @since 2026-03-19 21:28
 */
@Data
@Schema(description = "对话记录展示")
public class ConversationMemoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "对话ID")
    private Long id;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型(USER/ASSISTANT/SYSTEM)")
    private String type;

    @Schema(description = "轮次")
    private Integer roundNum;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
