package org.lixiyun.server.infrastructure.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件向量解析状态实时推送事件
 * <p>轻量状态事件，WebSocket 推送给管理端文件向量页；文件详情与向量列表仍以 REST 查询为准。</p>
 *
 * @author lixiyun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileVectorStatusEvent {

    /** 文件ID */
    private Long fileId;

    /**
     * 状态：
     * PARSING / READING / TRANSFORMING / VECTORIZING / WRITING / COMPLETED / FAILED / INTERRUPTED
     */
    private String status;

    /** 解析进度百分比（0-100）；无可靠总量时为 null */
    private Integer progress;

    /** 已处理粗粒度分块数（可靠时才有） */
    private Integer processedChunks;

    /** 总粗粒度分块数（可靠时才有） */
    private Integer totalChunks;

    /** 说明/失败原因（不含堆栈） */
    private String message;

    /** 同任务内递增序号，前端用于去重 */
    private long sequence;

    /** 服务端事件时间（epoch millis） */
    private long timestamp;
}