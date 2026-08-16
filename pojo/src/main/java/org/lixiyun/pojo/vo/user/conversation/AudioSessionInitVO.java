package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 语音会话初始化响应VO
 *
 * @author lixiyun
 * @since 2026-08-15 10:08
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "语音会话初始化响应")
public class AudioSessionInitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话ID", example = "2412321342421")
    private Long conversationId;

}