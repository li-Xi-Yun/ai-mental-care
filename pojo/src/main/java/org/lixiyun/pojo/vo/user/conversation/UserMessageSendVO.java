package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户消息发送响应VO
 *
 * @author lixiyun
 * @since 2026-07-14 16:36
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户消息发送响应")
public class UserMessageSendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话ID")
    private Long conversationId;

    @Schema(description = "当前轮次")
    private Integer currentRound;

}