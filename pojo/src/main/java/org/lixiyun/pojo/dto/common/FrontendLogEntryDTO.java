package org.lixiyun.pojo.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 前端日志条目
 *
 * @author lixiyun
 * @since 2026-09-27
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "前端日志条目")
public class FrontendLogEntryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "时间戳", example = "2026-09-27 14:30:12.345")
    private String timestamp;

    @Schema(description = "日志级别: INFO / WARN / ERROR", example = "ERROR")
    private String level;

    @Schema(description = "模块名", example = "ChatWebSocket")
    private String module;

    @Schema(description = "日志消息", example = "WebSocket连接超时")
    private String message;

    @Schema(description = "异常堆栈（仅 ERROR 级别）")
    private String stack;

}