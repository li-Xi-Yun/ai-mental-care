package org.lixiyun.pojo.vo.user.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 对话生命周期初始化响应VO
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "对话生命周期初始化响应")
public class ConversationLifecycleInitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话ID", example = "2412321342421")
    private Long conversationId;

    @Schema(description = "输入层端点信息列表")
    private List<EndpointInfo> inputEndpoints;

    @Schema(description = "输出层端点信息列表")
    private List<EndpointInfo> outputEndpoints;

    /**
     * 端点信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "端点信息")
    public static class EndpointInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "端点描述", example = "text-input")
        private String description;

        @Schema(description = "WebSocket 路径", example = "/ws/text/2412321342421")
        private String path;
    }
}