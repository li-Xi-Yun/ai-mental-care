package org.lixiyun.server.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.enums.KnowledgeBaseExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.entity.vector.VectorData;
import org.lixiyun.server.ai.rag.reader.FileReader;
import org.lixiyun.server.ai.rag.transformer.FileTransformer;
import org.lixiyun.server.ai.rag.writer.FileWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * RAG文件处理编排器。
 *
 * <ul>
 *     <li>{@link FileReader} 读取并粗分块</li>
 *     <li>{@link FileTransformer} 细粒度语义分割</li>
 *     <li>{@link FileWriter} 向量化入库</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-04-29 20:17
 */
@Slf4j
@Component
public class RagStore {

    private final FileReader fileReader;
    private final FileTransformer fileTransformer;
    private final FileWriter fileWriter;
    private final ThreadPoolTaskExecutor ragThreadPoolTaskExecutor;

    public RagStore(FileReader fileReader,
                    FileTransformer fileTransformer,
                    FileWriter fileWriter,
                    @Qualifier("ragThreadPoolTaskExecutor") ThreadPoolTaskExecutor ragThreadPoolTaskExecutor) {
        this.fileReader = fileReader;
        this.fileTransformer = fileTransformer;
        this.fileWriter = fileWriter;
        this.ragThreadPoolTaskExecutor = ragThreadPoolTaskExecutor;
    }

    /**
     * 存储文件到向量数据库。
     * <p>
     * 完整流程：FileReader 粗分块 -> FileTransformer 语义分割 -> FileWriter 向量化入库。
     * 解析被用户中断时通知 interruptedHandler 并视为正常结束。
     *
     * @param filePath          本地文件的绝对或相对路径，不能为空且文件必须存在
     * @param vectorData        文件元数据信息 {@link VectorData}
     * @param interruptedHandler 中断处理函数，可空
     */
    public void storeFileToVector(String filePath, VectorData vectorData, Consumer<Long> interruptedHandler) {
        Long fileId = vectorData.getFileId();
        try {
            fileReader.readLocalFile(filePath, vectorData, documents ->
                    fileTransformer.transformDocuments(documents, fileWriter::writeDocuments));
        } catch (BusinessException e) {
            if (e.getCode() != null && e.getCode().equals(KnowledgeBaseExceptionEnum.FILE_PARSE_INTERRUPTED.getCode())) {
                log.warn("RAG文件处理被用户中断，触发中断处理器，文件ID：{}", fileId);
                if (interruptedHandler != null) {
                    interruptedHandler.accept(fileId);
                }
                return; // 中断视为正常结束，不抛
            }
            throw e;
        }
    }

    /**
     * 异步存储文件到向量数据库。
     * <p>
     * 使用专用线程池异步执行RAG文件处理流程，避免阻塞主线程。
     *
     * @param filePath          本地文件的绝对或相对路径，不能为空且文件必须存在
     * @param vectorData        文件元数据信息 {@link VectorData}
     * @param interruptedHandler 中断处理函数，任务中断时需要执行的功能
     * @return CompletableFuture 对象，可用于监听任务完成状态或获取异常信息
     */
    public CompletableFuture<Void> storeFileToVectorAsync(String filePath,
                                                          VectorData vectorData,
                                                          Consumer<Long> interruptedHandler) {
        if (vectorData == null || vectorData.getFileId() == null) {
            throw new BusinessException(FileExceptionEnum.FILE_PARAMS_ERROR);
        }
        return CompletableFuture.runAsync(() -> {
            long fileId = vectorData.getFileId();
            try {
                // 清除可能存在的旧中断标识（防止重复任务干扰）
                RagInterruptManager.clearInterruptFlag(fileId);

                log.info("RAG异步任务开始处理文件: {}，文件ID: {}", filePath, fileId);
                storeFileToVector(filePath, vectorData, interruptedHandler);
                log.info("RAG异步任务完成处理文件: {}", filePath);
            } catch (Throwable e) {
                log.error("RAG异步任务处理文件失败: {}", filePath, e);
                throw e;
            }
        }, ragThreadPoolTaskExecutor);
    }
}