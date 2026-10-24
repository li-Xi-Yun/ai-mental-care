package org.lixiyun.server.infrastructure.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.websocket.utils.WebSocketUtils;
import org.lixiyun.server.socket.constant.FileVectorConstant;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文件向量解析状态推送器
 * <p>把轻量状态事件通过 WebSocket 推送给指定管理员；发布失败不影响 RAG 任务主流程（内部吞异常）。</p>
 *
 * @author lixiyun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileVectorStatusPublisher {

    /** 按文件ID维护的递增序号，用于前端去重 */
    private final ConcurrentHashMap<Long, AtomicLong> sequenceHolder = new ConcurrentHashMap<>();

    /**
     * 推送文件向量解析状态事件
     *
     * @param userId  目标用户ID（显式传入，不在异步线程读取 ThreadLocal）
     * @param fileId  文件ID
     * @param status  状态
     * @param progress 进度百分比，无可靠总量为 null
     * @param processedChunks 已处理粗粒度分块，不可靠为 null
     * @param totalChunks 总粗粒度分块，不可靠为 null
     * @param message 说明/失败原因，可为 null
     */
    public void publish(Long userId, Long fileId, String status, Integer progress,
                        Integer processedChunks, Integer totalChunks, String message) {
        if (userId == null || fileId == null) {
            return;
        }
        try {
            long sequence = sequenceHolder.computeIfAbsent(fileId, k -> new AtomicLong()).incrementAndGet();
            FileVectorStatusEvent event = FileVectorStatusEvent.builder()
                    .fileId(fileId)
                    .status(status)
                    .progress(progress)
                    .processedChunks(processedChunks)
                    .totalChunks(totalChunks)
                    .message(message)
                    .sequence(sequence)
                    .timestamp(System.currentTimeMillis())
                    .build();
            WebSocketUtils.sendToUserBySubDestination(
                    userId.toString(),
                    FileVectorConstant.FILE_VECTOR_STATUS + "/" + fileId,
                    event
            );
            log.debug("文件向量状态推送，用户：{}，文件：{}，状态：{}", userId, fileId, status);
        } catch (Exception e) {
            // 推送失败只记录日志，不影响文件解析主流程
            log.error("文件向量状态推送失败：用户={}，文件={}，状态={}", userId, fileId, status, e);
        }
    }
}