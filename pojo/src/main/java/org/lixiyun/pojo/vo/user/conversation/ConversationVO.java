package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author lixiyun
 * @since 2026-03-19 22:48
 */
@Data
@Schema(description = "会话列表展示")
public class ConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话ID")
    private Long id;

    @Schema(description = "会话名称")
    private String name;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

}
